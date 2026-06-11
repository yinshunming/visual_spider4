package com.visualspider.service;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

public final class XPathGenerator {

    private XPathGenerator() {
    }

    public static String generate(Element target, Document document) {
        if (target == null) {
            return null;
        }
        List<Element> chain = new ArrayList<>();
        Element current = target;
        while (current != null) {
            chain.add(0, current);
            current = current.parent();
        }
        Element ancestorWithId = findIdAncestor(chain, document);
        if (ancestorWithId != null) {
            int idx = chain.indexOf(ancestorWithId);
            StringBuilder sb = new StringBuilder("//*[@id='").append(ancestorWithId.attr("id")).append("']");
            for (int i = idx + 1; i < chain.size(); i++) {
                sb.append("//").append(segmentFor(chain.get(i), chain.get(i - 1)));
            }
            return sb.toString();
        }
        int start = skipImplicitLayers(chain);
        StringBuilder sb = new StringBuilder("//");
        for (int i = start; i < chain.size(); i++) {
            Element el = chain.get(i);
            Element parent = i == 0 ? null : chain.get(i - 1);
            if (i > start) {
                sb.append("/");
            }
            sb.append(segmentFor(el, parent));
        }
        return sb.toString();
    }

    private static int skipImplicitLayers(List<Element> chain) {
        int i = 0;
        while (i < chain.size()) {
            String tag = chain.get(i).tagName();
            if (tag.equals("#root") || tag.equals("html") || tag.equals("head") || tag.equals("body")) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static Element findIdAncestor(List<Element> chain, Document document) {
        for (Element el : chain) {
            String id = el.attr("id");
            if (id != null && !id.isEmpty() && isIdUniqueInDocument(id, document)) {
                return el;
            }
        }
        return null;
    }

    private static String segmentFor(Element element, Element parent) {
        StringBuilder part = new StringBuilder(element.tagName());
        String cls = element.attr("class");
        if (!cls.isEmpty()) {
            String firstClass = cls.split("\\s+")[0];
            if (!firstClass.isEmpty()) {
                part.append("[contains(@class,'").append(firstClass).append("')]");
            }
        }
        if (parent != null) {
            int sameTagSiblings = 0;
            int index = 0;
            for (Element child : parent.children()) {
                if (child.tagName().equals(element.tagName())) {
                    sameTagSiblings++;
                    if (child == element) {
                        index = sameTagSiblings;
                    }
                }
            }
            if (sameTagSiblings > 1) {
                part.append("[").append(index).append("]");
            }
        }
        return part.toString();
    }

    private static boolean isIdUniqueInDocument(String id, Document document) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return document.select("#" + id).size() == 1;
    }
}
