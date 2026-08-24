package com.lamphaus.core.provider

import com.lamphaus.core.model.CatalogQuery
import com.lamphaus.core.model.MediaDetail
import com.lamphaus.core.model.MediaPreview
import com.lamphaus.core.model.ProviderManifest
import com.lamphaus.core.model.ProviderResult
import com.lamphaus.core.model.StreamCandidate
import com.lamphaus.core.model.SubtitleTrack

interface ProviderClient {
    suspend fun manifest(manifestUrl: String): ProviderResult<ProviderManifest>

    suspend fun discoverProviderUrls(catalogUrl: String): ProviderResult<List<String>>

    suspend fun catalog(
        manifestUrl: String,
        providerId: String,
        query: CatalogQuery,
    ): ProviderResult<List<MediaPreview>>

    suspend fun meta(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<MediaDetail>

    suspend fun streams(
        manifestUrl: String,
        providerId: String,
        type: String,
        id: String,
    ): ProviderResult<List<StreamCandidate>>

    suspend fun subtitles(
        manifestUrl: String,
        type: String,
        id: String,
    ): ProviderResult<List<SubtitleTrack>>
}

