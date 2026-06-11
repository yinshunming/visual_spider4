package com.visualspider.service;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

public final class CssSelectorGenerator {

    private CssSelectorGenerator() {
    }

    public static String generate(Element element) {
        if (element == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        Element current = element;
        while (current != null && !isDocumentRoot(current)) {
            parts.add(0, segmentFor(current));
            String id = current.attr("id");
            if (!id.isEmpty() && isIdUniqueInDocument(id, current)) {
                parts.clear();
                parts.add("#" + escapeCssIdent(id));
                break;
            }
            current = current.parent();
        }
        return String.join(" > ", parts);
    }

    private static String segmentFor(Element el) {
        StringBuilder sb = new StringBuilder(el.tagName());
        String id = el.attr("id");
        if (!id.isEmpty()) {
            sb.append("#").append(escapeCssIdent(id));
            return sb.toString();
        }
        String cls = el.attr("class");
        if (!cls.isEmpty()) {
            String firstClass = cls.split("\\s+")[0];
            if (!firstClass.isEmpty()) {
                sb.append(".").append(escapeCssIdent(firstClass));
            }
        }
        Element parent = el.parent();
        if (parent != null) {
            int sameTagCount = 0;
            int index = 0;
            for (Element child : parent.children()) {
                if (child.tagName().equals(el.tagName())) {
                    sameTagCount++;
                    if (child == el) {
                        index = sameTagCount;
                    }
                }
            }
            if (sameTagCount > 1) {
                sb.append(":nth-of-type(").append(index).append(")");
            }
        }
        return sb.toString();
    }

    private static boolean isIdUniqueInDocument(String id, Element el) {
        Element doc = el.ownerDocument();
        if (doc == null) return false;
        return doc.select("#" + id).size() == 1;
    }

    private static boolean isDocumentRoot(Element el) {
        return el.parent() == null;
    }

    private static String escapeCssIdent(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
