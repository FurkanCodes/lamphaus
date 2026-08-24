package com.lamphaus.app.ui

import com.lamphaus.core.model.Episode
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.MediaType

internal object PreviewMedia {
    val aurora = MediaPreview(
        id = "fixture:aurora",
        type = MediaType.MOVIE,
        rawType = "movie",
        name = "The Last Aurora",
        description = "A cartographer follows a fading signal across a polar night.",
        releaseYear = 2026,
        genres = listOf("Drama", "Science fiction"),
        contentRating = "PG-13",
    )
    val glass = MediaPreview(
        id = "fixture:glass",
        type = MediaType.SERIES,
        rawType = "series",
        name = "Glass District",
        description = "An architect discovers that an unfinished city remembers its residents.",
        releaseYear = 2025,
        genres = listOf("Mystery", "Drama"),
        contentRating = "TV-14",
    )
    val tide = MediaPreview(
        id = "fixture:tide",
        type = MediaType.MOVIE,
        rawType = "movie",
        name = "A Measure of Tide",
        description = "Two siblings return to an island where every clock runs differently.",
        releaseYear = 2024,
        genres = listOf("Adventure"),
        contentRating = "PG",
    )
    val orbit = MediaPreview(
        id = "fixture:orbit",
        type = MediaType.SERIES,
        rawType = "series",
        name = "Small Orbit",
        description = "A patient crew keeps a weather station alive above a changing planet.",
        releaseYear = 2026,
        genres = listOf("Science fiction"),
        contentRating = "TV-PG",
    )
    val items = listOf(aurora, glass, tide, orbit)
    val episodes = listOf(
        Episode("fixture:glass:1:1", "A City Without Doors", 1, 1, "Mara arrives before the district opens."),
        Episode("fixture:glass:1:2", "Refraction", 1, 2, "A reflection points toward an abandoned plan."),
        Episode("fixture:glass:1:3", "Night Survey", 1, 3, "The team maps a street that should not exist."),
    )
}

