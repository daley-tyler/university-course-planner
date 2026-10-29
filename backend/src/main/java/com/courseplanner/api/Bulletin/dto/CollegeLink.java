package com.courseplanner.api.Bulletin.dto;

public record CollegeLink(
        String name,   // "School of Business"
        String path,   // "/business/"
        String url     // "https://catalog.example.edu/business/"
) {}
