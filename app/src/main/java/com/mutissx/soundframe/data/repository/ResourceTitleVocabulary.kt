package com.mutissx.soundframe.data.repository

import android.content.Context
import com.mutissx.soundframe.R
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.TitleVocabulary
import com.mutissx.soundframe.domain.repository.TitleVocabularyProvider

/** Reads title words from resources on every call, so a language change applies immediately. */
class ResourceTitleVocabulary(private val context: Context) : TitleVocabularyProvider {
    override fun vocabulary(): TitleVocabulary {
        val r = context.resources
        return TitleVocabulary(
            pattern = r.getString(R.string.title_pattern),
            quietDark = r.getStringArray(R.array.title_adj_quiet_dark).toList(),
            quietBright = r.getStringArray(R.array.title_adj_quiet_bright).toList(),
            loudDark = r.getStringArray(R.array.title_adj_loud_dark).toList(),
            loudBright = r.getStringArray(R.array.title_adj_loud_bright).toList(),
            nouns = mapOf(
                ArtStyle.AURA to r.getStringArray(R.array.title_nouns_aura).toList(),
                ArtStyle.BLOOM to r.getStringArray(R.array.title_nouns_bloom).toList(),
                ArtStyle.PARTICLES to r.getStringArray(R.array.title_nouns_particles).toList(),
                ArtStyle.TOPOGRAPHY to r.getStringArray(R.array.title_nouns_topography).toList(),
                ArtStyle.ECHO to r.getStringArray(R.array.title_nouns_echo).toList(),
            ),
        )
    }
}
