package com.example;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One physical or online item in the catalog. A plain data holder; the fields mirror your tables:
 * Item, Record_Info, Location and the media-type tables (Book, Article, Periodical, ...).
 * Not every field applies to every type (a Book has an ISBN, a Projector does not): leave the rest null.
 */
public class MediaItem {

    /**
     * PLACEHOLDERS: rename these to the real information types you decide on, or delete them.
     * Each name becomes a key in {@link #custom}, and shows up in the item's details.
     */
    public static final List<String> PLACEHOLDER_FIELDS = List.of(
            "Placeholder Field 1 (rename me)",
            "Placeholder Field 2 (rename me)",
            "Placeholder Field 3 (rename me)");

    // ---- Item table ----
    public final String barcode;            // Item.Barcode (primary key)
    public final MediaType mediaType;
    public String callNumber;               // Item.Call_Number
    public ItemStatus status = ItemStatus.AVAILABLE;   // Item.Status
    public String condition;                // Item.Condition (Good, Fair, ...)
    public String onlineRef;                // Item.Online_Ref (link, when available online)

    // ---- Location table ----
    public Integer floorNum;                // Location.Floor_Num
    public String collectionArea;           // Location.Collection_Area

    // ---- Record_Info table ----
    public String title;                    // Record_Info.Title
    public String author;                   // Record_Info.Author
    public String publisher;                // Record_Info.Publisher
    public String description;              // Record_Info.Description
    public Integer pubYear;                 // Record_Info.Pub_Year
    public String genre;                    // Record_Info.Genre

    // ---- Media-type specific tables ----
    public String isbn;                     // Book.ISBN
    public Integer pageCount;               // Print_Media.Page_Count (Book, Article, Periodical)
    public String doi;                      // Article.DOI
    public String issn;                     // Periodical.ISSN
    public String periodicalType;           // Periodical.Type (Journal, Magazine, ...)
    public Integer runtimeMinutes;          // Video_Audio_Media.Runtime
    public String formatType;               // Video_Audio_Media.Format_Type (CD, DVD, ...)
    public String equipmentType;            // Equipment.Type

    /** Values for PLACEHOLDER_FIELDS (and any extra fields you add later). */
    public final Map<String, String> custom = new LinkedHashMap<>();

    public MediaItem(String barcode, MediaType mediaType) {
        this.barcode = barcode;
        this.mediaType = mediaType;
        for (String name : PLACEHOLDER_FIELDS) {
            custom.put(name, "TBD");
        }
    }

    // Fluent setters keep the sample data readable.
    public MediaItem call(String v) { callNumber = v; return this; }
    public MediaItem status(ItemStatus v) { status = v; return this; }
    public MediaItem condition(String v) { condition = v; return this; }
    public MediaItem online(String v) { onlineRef = v; return this; }
    public MediaItem location(int floor, String area) { floorNum = floor; collectionArea = area; return this; }
    public MediaItem record(String title, String author, String publisher, Integer year, String genre,
                            String description) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.pubYear = year;
        this.genre = genre;
        this.description = description;
        return this;
    }
    public MediaItem book(String isbn, int pages) { this.isbn = isbn; pageCount = pages; return this; }
    public MediaItem article(String doi, int pages) { this.doi = doi; pageCount = pages; return this; }
    public MediaItem periodical(String issn, String type, int pages) {
        this.issn = issn;
        periodicalType = type;
        pageCount = pages;
        return this;
    }
    public MediaItem video(int minutes, String format) { runtimeMinutes = minutes; formatType = format; return this; }
    public MediaItem equipment(String type) { equipmentType = type; return this; }

    // ---- Helpers for the screens ----

    /** Equipment has no Record_Info row, so fall back to its equipment type. */
    public String displayTitle() {
        if (title != null) {
            return title;
        }
        return equipmentType != null ? equipmentType : "(untitled)";
    }

    public String locationText() {
        if (floorNum == null && collectionArea == null) {
            return "";
        }
        return "Floor " + floorNum + ", " + collectionArea;
    }

    public boolean isAvailable() {
        return status == ItemStatus.AVAILABLE;
    }

    /**
     * The status this viewer is allowed to see. Only admins see Overdue; regular users just see
     * that the item is Checked Out. Every screen should show status through this method.
     */
    public ItemStatus statusFor(Role viewer) {
        if (status == ItemStatus.OVERDUE && viewer != Role.ADMIN) {
            return ItemStatus.CHECKED_OUT;
        }
        return status;
    }

    /**
     * Every piece of information worth showing for this item, in display order, skipping blanks.
     * The item details window (next step) will simply loop over this.
     */
    public Map<String, String> details(Role viewer) {
        Map<String, String> d = new LinkedHashMap<>();
        put(d, "Title", displayTitle());
        put(d, "Media type", mediaType.toString());
        put(d, "Author", author);
        put(d, "Genre", genre);
        put(d, "Publisher", publisher);
        put(d, "Year published", pubYear);
        put(d, "Description", description);
        put(d, "Barcode", barcode);
        put(d, "Call number", callNumber);
        put(d, "Status", statusFor(viewer));
        put(d, "Location", locationText());
        put(d, "Condition", condition);
        put(d, "Available online", onlineRef);
        put(d, "ISBN", isbn);
        put(d, "DOI", doi);
        put(d, "ISSN", issn);
        put(d, "Periodical type", periodicalType);
        put(d, "Page count", pageCount);
        put(d, "Runtime (minutes)", runtimeMinutes);
        put(d, "Format", formatType);
        put(d, "Equipment type", equipmentType);
        d.putAll(custom);
        return d;
    }

    private static void put(Map<String, String> d, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            d.put(label, value.toString());
        }
    }
}
