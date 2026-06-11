package com.visualspider.service;

import com.visualspider.dto.response.SelectorCandidate;
import com.visualspider.dto.response.SelectorPairResponse;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SelectorCraftService {

    private static final int SAMPLE_LIMIT = 5;

    public SelectorPairResponse craft(Element element, Document document) {
        if (element == null) {
            return new SelectorPairResponse(null, null);
        }
        String css = CssSelectorGenerator.generate(element);
        String xpath = XPathGenerator.generate(element, document);
        SelectorCandidate cssCandidate = buildCandidate(css, document, true);
        SelectorCandidate xpathCandidate = buildCandidate(xpath, document, false);
        return new SelectorPairResponse(cssCandidate, xpathCandidate);
    }

    private SelectorCandidate buildCandidate(String selector, Document document, boolean isCss) {
        if (selector == null || selector.isEmpty()) {
            return null;
        }
        List<Element> matches = isCss ? document.select(selector) : evaluateXPath(selector, document);
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
        return new SelectorCandidate(selector, matches.size(), samples);
    }

    private List<Element> evaluateXPath(String xpath, Document document) {
        try {
            org.jsoup.select.Evaluator evaluator = org.jsoup.select.QueryParser.parse(xpath.replaceAll("^/+", ""));
            return new ArrayList<>(document.select(evaluator));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
