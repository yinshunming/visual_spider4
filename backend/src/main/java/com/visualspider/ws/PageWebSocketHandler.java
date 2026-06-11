package com.visualspider.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualspider.dto.request.CreateFieldRequest;
import com.visualspider.dto.response.SelectorPairResponse;
import com.visualspider.dto.ws.ClickPayload;
import com.visualspider.dto.ws.ErrorPayload;
import com.visualspider.dto.ws.LoadPagePayload;
import com.visualspider.dto.ws.PreviewPayload;
import com.visualspider.dto.ws.PreviewResultPayload;
import com.visualspider.dto.ws.SaveFieldPayload;
import com.visualspider.dto.ws.SaveFieldResultPayload;
import com.visualspider.dto.ws.ScreenshotPayload;
import com.visualspider.dto.ws.StatePayload;
import com.visualspider.dto.ws.WsMessage;
import com.visualspider.entity.CrawlField;
import com.visualspider.exception.BusinessException;
import com.visualspider.service.BrowserSessionService;
import com.visualspider.service.CrawlFieldService;
import com.visualspider.service.SelectorCraftService;
import com.visualspider.service.SelectorHighlighter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PageWebSocketHandler extends AbstractWebSocketHandler {

    private final Map<String, String> sessionToConfig = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);

    private final BrowserSessionService browserService;
    private final SelectorCraftService selectorService;
    private final SelectorHighlighter highlighter;
    private final CrawlFieldService fieldService;

    public PageWebSocketHandler(BrowserSessionService browserService,
                                SelectorCraftService selectorService,
                                SelectorHighlighter highlighter,
                                CrawlFieldService fieldService) {
        this.browserService = browserService;
        this.selectorService = selectorService;
        this.highlighter = highlighter;
        this.fieldService = fieldService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionToConfig.put(session.getId(), "");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionToConfig.remove(session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        WsMessage<?> envelope;
        try {
            envelope = mapper.readValue(message.getPayload(), WsMessage.class);
        } catch (Exception e) {
            sendError(session, "BAD_REQUEST", "消息格式错误: " + e.getMessage());
            return;
        }
        String type = envelope.type();
        Object payload = envelope.payload();
        try {
            switch (type == null ? "" : type) {
                case "load" -> handleLoad(session, payload);
                case "click" -> handleClick(session, payload);
                case "preview" -> handlePreview(session, payload);
                case "saveField" -> handleSaveField(session, payload);
                case "close" -> handleClose(session);
                default -> sendError(session, "UNKNOWN", "未知消息类型: " + type);
            }
        } catch (BusinessException e) {
            sendError(session, mapBusinessCode(e), e.getMessage());
        } catch (Exception e) {
            sendError(session, "UNKNOWN", e.getMessage());
        }
    }

    private void handleLoad(WebSocketSession session, Object payload) throws IOException {
        LoadPagePayload lp = mapper.convertValue(payload, LoadPagePayload.class);
        if (lp.url() == null || lp.url().isBlank()) {
            sendError(session, "BAD_REQUEST", "URL 不能为空");
            return;
        }
        if (lp.configId() != null) {
            sessionToConfig.put(session.getId(), String.valueOf(lp.configId()));
        }
        browserService.load(lp.url());
        send(session, "state", StatePayload.loaded());
        pushScreenshot(session);
    }

    private void handleClick(WebSocketSession session, Object payload) throws IOException {
        ClickPayload cp = mapper.convertValue(payload, ClickPayload.class);
        var page = browserService.getPage();
        if (page == null) {
            sendError(session, "NO_SESSION", "浏览器未就绪");
            return;
        }
        Object elemInfo = page.evaluate(
                "({x, y}) => { const el = document.elementFromPoint(x, y); if (!el) return null; const path=[]; let n=el; while(n && n.nodeType===1) { const p=n.parentElement; const sibs = p ? Array.from(p.children).filter(c=>c.tagName===n.tagName) : []; const idx = sibs.indexOf(n)+1; path.unshift(n.tagName.toLowerCase() + (sibs.length>1 ? ':nth-of-type(' + idx + ')' : '')); n = p; } return { tagPath: path.join(' > ') }; }",
                java.util.Map.of("x", cp.x(), "y", cp.y()));
        if (elemInfo == null) {
            sendError(session, "NO_ELEMENT", "未命中任何元素");
            return;
        }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> info = (java.util.Map<String, Object>) elemInfo;
        String tagPath = (String) info.get("tagPath");
        String docHtml = (String) page.evaluate("() => document.documentElement.outerHTML");
        Document doc = Jsoup.parse(docHtml);
        Element target = findByTagPath(doc, tagPath);
        if (target == null) {
            sendError(session, "NO_ELEMENT", "未命中任何元素");
            return;
        }
        SelectorPairResponse pair = selectorService.craft(target, doc);
        send(session, "selectors", pair);
    }

    private Element findByTagPath(Document doc, String tagPath) {
        String[] parts = tagPath.split(" > ");
        Element current = null;
        for (String part : parts) {
            String[] segs = part.split(":nth-of-type\\(");
            String tag = segs[0];
            int nth = 0;
            if (segs.length > 1) {
                nth = Integer.parseInt(segs[1].replace(")", ""));
            }
            List<Element> children = current == null ? doc.getAllElements() : current.children();
            List<Element> matching = new java.util.ArrayList<>();
            for (Element c : children) {
                if (c.tagName().equalsIgnoreCase(tag)) {
                    matching.add(c);
                }
            }
            if (nth == 0) {
                if (matching.isEmpty()) return null;
                current = matching.get(0);
            } else {
                if (matching.size() < nth) return null;
                current = matching.get(nth - 1);
            }
        }
        return current;
    }

    private void handlePreview(WebSocketSession session, Object payload) throws IOException {
        PreviewPayload pp = mapper.convertValue(payload, PreviewPayload.class);
        if (pp.selector() == null || pp.selector().isBlank()) {
            sendError(session, "BAD_REQUEST", "selector 不能为空");
            return;
        }
        String cssSelector = pp.selectorType() == null
                || pp.selectorType() == com.visualspider.enums.SelectorType.CSS
                ? pp.selector()
                : toCssFromXPath(pp.selector());
        PreviewResultPayload result = highlighter.previewResult(cssSelector);
        send(session, "previewResult", result);
        if (browserService.getPage() != null) {
            highlighter.highlightAndCount(cssSelector);
            pushScreenshot(session);
        }
    }

    private void handleSaveField(WebSocketSession session, Object payload) throws IOException {
        SaveFieldPayload sp = mapper.convertValue(payload, SaveFieldPayload.class);
        String configIdStr = sessionToConfig.get(session.getId());
        if (configIdStr == null || configIdStr.isEmpty()) {
            send(session, "saveFieldResult",
                    new SaveFieldResultPayload(false, null, "请先发送 load 消息并携带 configId"));
            return;
        }
        Long configId = Long.valueOf(configIdStr);
        try {
            CreateFieldRequest req = new CreateFieldRequest(sp.pageType(), sp.fieldName(), sp.fieldType(), sp.selector());
            CrawlField field = new CrawlField();
            field.setPageType(sp.pageType());
            field.setFieldName(sp.fieldName());
            field.setFieldType(sp.fieldType());
            field.setSelector(sp.selector());
            field.setConfig(new com.visualspider.entity.CrawlConfig());
            field.getConfig().setId(configId);
            CrawlField saved = fieldService.addField(configId, field);
            send(session, "saveFieldResult", new SaveFieldResultPayload(true, saved.getId(), null));
        } catch (BusinessException e) {
            send(session, "saveFieldResult", new SaveFieldResultPayload(false, null, e.getMessage()));
        }
    }

    private void handleClose(WebSocketSession session) throws IOException {
        browserService.close();
        send(session, "state", StatePayload.closed());
    }

    private void pushScreenshot(WebSocketSession session) throws IOException {
        byte[] bytes = highlighter.screenshotBytes();
        if (bytes == null || bytes.length == 0) {
            return;
        }
        String b64 = Base64.getEncoder().encodeToString(bytes);
        send(session, "screenshot", new ScreenshotPayload(b64));
    }

    private void send(WebSocketSession session, String type, Object payload) throws IOException {
        WsMessage<Object> msg = new WsMessage<>(type, payload);
        String json = mapper.writeValueAsString(msg);
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }

    private void sendError(WebSocketSession session, String code, String message) throws IOException {
        send(session, "error", new ErrorPayload(code, message));
    }

    private String mapBusinessCode(BusinessException e) {
        int code = e.getCode();
        return switch (code) {
            case 4006 -> "NAVIGATION_FAILED";
            case 409 -> "ALREADY_ACTIVE";
            case 404 -> "NOT_FOUND";
            default -> "BUSINESS";
        };
    }

    private String toCssFromXPath(String xpath) {
        String trimmed = xpath.replaceAll("^/+", "");
        String[] parts = trimmed.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (i > 0) sb.append(" ");
            String seg = parts[i];
            if (seg.startsWith("*[@id=")) {
                sb.append("#").append(seg.substring(seg.indexOf("'") + 1, seg.lastIndexOf("'")));
                continue;
            }
            String tag = seg;
            StringBuilder predicates = new StringBuilder();
            int lb = seg.indexOf('[');
            if (lb > 0) {
                tag = seg.substring(0, lb);
                predicates.append(seg, lb, seg.length());
            }
            sb.append(tag);
            if (predicates.length() > 0) {
                sb.append(predicates);
            }
        }
        return sb.toString();
    }
}
