package com.example;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Hard-coded sample items so the Catalog screen has something to show. TODO: replace with a MySQL version. */
public class SampleCatalogRepository implements CatalogRepository {

    private final List<MediaItem> items = List.of(
            new MediaItem("BC100", MediaType.BOOK).call("FIC-ORW-1984").condition("Good")
                    .location(1, "Fiction").status(ItemStatus.CHECKED_OUT)
                    .record("1984", "George Orwell", "Secker & Warburg", 1949, "Fiction", "Dystopian novel")
                    .book("978-0451524935", 328),
            new MediaItem("BC101", MediaType.BOOK).call("SCI-HAW-BRI").condition("Excellent")
                    .location(2, "Non-Fiction").status(ItemStatus.OVERDUE)
                    .record("A Brief History of Time", "Stephen Hawking", "Bantam Books", 1988, "Science",
                            "Cosmology book")
                    .book("978-0553380163", 256),
            new MediaItem("BC102", MediaType.VIDEO_AUDIO).call("MUS-BEA-ABB").condition("Fair")
                    .location(3, "Multimedia").status(ItemStatus.ON_HOLD)
                    .online("http://library.towson.edu/stream/103")
                    .record("Abbey Road", "The Beatles", "Apple Records", 1969, "Music", "Music album")
                    .video(47, "CD"),
            new MediaItem("BC103", MediaType.EQUIPMENT).call("EQ-PROJ-01").condition("Good")
                    .location(3, "Multimedia").equipment("Projector"),
            new MediaItem("BC104", MediaType.BOOK).call("FIC-HER-DUN").condition("Good")
                    .location(1, "Fiction")
                    .record("Dune", "Frank Herbert", "Chilton Books", 1965, "Fiction", "Science fiction novel")
                    .book("978-0441013593", 688),
            new MediaItem("BC105", MediaType.PERIODICAL).call("PER-NAT-600").condition("Good")
                    .location(2, "Non-Fiction").online("https://library.example.edu/periodicals/nature")
                    .record("Nature, Vol. 600", "Various", "Springer Nature", 2021, "Science",
                            "Weekly science journal issue")
                    .periodical("0028-0836", "Journal", 96),
            new MediaItem("BC106", MediaType.ARTICLE).call("ART-HAW-BLK").condition("Good")
                    .location(2, "Non-Fiction").online("https://library.example.edu/articles/black-holes")
                    .record("Information Loss in Black Holes", "Stephen Hawking", "APS", 2005, "Science",
                            "Journal article on the black hole information paradox")
                    .article("10.1103/PhysRevD.72.084013", 13),
            new MediaItem("BC107", MediaType.BOOK).call("FIC-AUS-PRI").condition("Good")
                    .location(1, "Fiction")
                    .record("Pride and Prejudice", "Jane Austen", "T. Egerton", 1813, "Romance",
                            "Classic novel of manners")
                    .book("978-0141439518", 432),
            new MediaItem("BC108", MediaType.BOOK).call("HIS-HAR-SAP").condition("Excellent")
                    .location(2, "Non-Fiction").status(ItemStatus.CHECKED_OUT)
                    .record("Sapiens", "Yuval Noah Harari", "Harper", 2015, "History",
                            "A brief history of humankind")
                    .book("978-0062316097", 464),
            new MediaItem("BC109", MediaType.VIDEO_AUDIO).call("FILM-NOL-INC").condition("Good")
                    .location(3, "Multimedia")
                    .record("Inception", "Christopher Nolan", "Warner Bros.", 2010, "Science Fiction",
                            "Feature film")
                    .video(148, "DVD"),
            new MediaItem("BC110", MediaType.PERIODICAL).call("PER-SCI-AM").condition("Fair")
                    .location(2, "Non-Fiction")
                    .record("Scientific American, March 2024", "Various", "Springer Nature", 2024, "Science",
                            "Monthly science magazine")
                    .periodical("0036-8733", "Magazine", 84),
            new MediaItem("BC111", MediaType.EQUIPMENT).call("EQ-LAP-02").condition("Good")
                    .location(3, "Multimedia").status(ItemStatus.CHECKED_OUT).equipment("Laptop"),
            new MediaItem("BC112", MediaType.BOOK).call("FIC-TOL-HOB").condition("Good")
                    .location(1, "Fiction")
                    .record("The Hobbit", "J.R.R. Tolkien", "Allen & Unwin", 1937, "Fantasy",
                            "Fantasy adventure novel")
                    .book("978-0547928227", 300));

    @Override
    public List<MediaItem> search(CatalogQuery q) {
        String text = q.text() == null ? "" : q.text().trim().toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(i -> q.mediaType() == null || i.mediaType == q.mediaType())
                .filter(i -> q.genre() == null || q.genre().equalsIgnoreCase(i.genre))
                .filter(i -> !q.availableOnly() || i.isAvailable())
                .filter(i -> text.isEmpty() || matches(i, q.field(), text))
                .sorted(Comparator.comparing(i -> i.displayTitle().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    @Override
    public MediaItem findByBarcode(String barcode) {
        return items.stream().filter(i -> i.barcode.equalsIgnoreCase(barcode)).findFirst().orElse(null);
    }

    @Override
    public List<String> genres() {
        return items.stream().map(i -> i.genre).filter(g -> g != null && !g.isBlank())
                .distinct().sorted().collect(Collectors.toList());
    }

    private static boolean matches(MediaItem i, CatalogQuery.Field field, String text) {
        return switch (field) {
            case TITLE -> contains(i.displayTitle(), text);
            case AUTHOR -> contains(i.author, text);
            case ID_NUMBER -> contains(i.barcode, text) || contains(i.isbn, text) || contains(i.issn, text)
                    || contains(i.doi, text) || contains(i.callNumber, text);
            case ANY -> contains(i.displayTitle(), text) || contains(i.author, text)
                    || contains(i.genre, text) || contains(i.publisher, text) || contains(i.description, text)
                    || contains(i.barcode, text) || contains(i.isbn, text) || contains(i.issn, text)
                    || contains(i.doi, text) || contains(i.callNumber, text)
                    || contains(i.mediaType.toString(), text) || contains(i.equipmentType, text);
        };
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }
}
