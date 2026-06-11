package com.visualspider.service;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import com.visualspider.dto.ws.PreviewResultPayload;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SelectorHighlighter {

    private static final int SAMPLE_LIMIT = 5;

    private final BrowserSessionService browserService;

    public SelectorHighlighter(BrowserSessionService browserService) {
        this.browserService = browserService;
    }

    public int highlightAndCount(String selector) {
        Page page = browserService.getPage();
        if (page == null) {
            return 0;
        }
        Object result = page.evaluate(
                "(sel) => { document.querySelectorAll('.vs-highlight').forEach(e => e.classList.remove('vs-highlight')); const matches = document.querySelectorAll(sel); matches.forEach(e => e.classList.add('vs-highlight')); return matches.length; }",
                selector);
        return toInt(result);
    }

    public byte[] screenshotBytes() {
        Page page = browserService.getPage();
        if (page == null) {
            return new byte[0];
        }
        return page.screenshot(new Page.ScreenshotOptions().setType(ScreenshotType.PNG));
    }

    public PreviewResultPayload previewResult(String selector) {
        Document doc = currentDocument();
        if (doc == null || selector == null || selector.isEmpty()) {
            return new PreviewResultPayload(0, List.of());
        }
        List<Element> matches;
        try {
            matches = doc.select(selector);
        } catch (Exception e) {
            return new PreviewResultPayload(0, List.of());
        }
        List<String> samples = new ArrayList<>();
        for (Element el : matches) {
            if (samples.size() >= SAMPLE_LIMIT) break;
            String text = el.text() == null ? "" : el.text().trim();
            if (text.isEmpty()) {
                text = el.outerHtml();
                if (text.length() > 80) {
                    text = text.substring(0, 80);
                }
            }
            samples.add(text);
        }
        return new PreviewResultPayload(matches.size(), samples);
    }

    private Document currentDocument() {
        Page page = browserService.getPage();
        if (page == null) {
            return null;
        }
        try {
            String html = (String) page.evaluate("() => document.documentElement.outerHTML");
            return Jsoup.parse(html);
        } catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object result) {
        if (result instanceof Number n) {
            return n.intValue();
        }
        if (result instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
