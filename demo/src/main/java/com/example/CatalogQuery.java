package com.example;

/**
 * What the user typed/selected on the Catalog screen. The screen builds one of these and hands it to a
 * CatalogRepository. A database version turns it into a SQL WHERE clause with PreparedStatement parameters.
 *
 * @param text          words to look for (null/blank = no text filter)
 * @param field         which field the text is matched against
 * @param mediaType     only this type (null = all types)
 * @param genre         only this genre (null = all genres)
 * @param availableOnly only items that can be borrowed right now
 */
public record CatalogQuery(String text, Field field, MediaType mediaType, String genre, boolean availableOnly) {

    /** Which field the search text is compared with. */
    public enum Field {
        ANY("Any field"),
        TITLE("Title"),
        AUTHOR("Author"),
        ID_NUMBER("Barcode / ISBN / ISSN / DOI");

        private final String label;

        Field(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static CatalogQuery everything() {
        return new CatalogQuery(null, Field.ANY, null, null, false);
    }
}
