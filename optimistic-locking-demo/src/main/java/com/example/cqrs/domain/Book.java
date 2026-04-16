package com.example.cqrs.domain;

import java.util.List;

public record Book(String version, String isbn, String title, List<String> authors) {}
