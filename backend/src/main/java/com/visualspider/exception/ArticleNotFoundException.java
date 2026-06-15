package com.visualspider.exception;

public class ArticleNotFoundException extends BusinessException {
    public ArticleNotFoundException(Long id) {
        super(4042, "Article not found: id=" + id);
    }
}