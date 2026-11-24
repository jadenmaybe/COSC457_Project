package com.example;

/**
 * The kinds of media the library holds.
 * Database: matches which subtype table an item appears in
 * (Book, Article, Periodical, Video_Audio_Media, Equipment).
 */
public enum MediaType {
    BOOK("Book"),
    ARTICLE("Article"),
    PERIODICAL("Periodical"),
    VIDEO_AUDIO("Video/Audio"),
    EQUIPMENT("Equipment");
    // TODO: add any other media types your project needs (e.g. Map, Game, Microfilm).

    private final String label;

    MediaType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
