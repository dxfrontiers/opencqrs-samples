package com.example.cqrs.http;

import java.util.List;

public record BookView(String isbn, String title, List<String> authors, String version) {}
