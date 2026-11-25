package com.example;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** MySQL-backed catalog. Replaces SampleCatalogRepository. */
public class MySqlCatalogRepository implements CatalogRepository {

    /** Columns that describe one item. Shared with MySqlCheckoutRepository so both build MediaItems the same way. */
    static final String ITEM_COLUMNS = """
            i.Barcode, i.Call_Number, i.`Status`, i.`Condition`, i.Online_Ref,
            i.Floor_Num, i.Collection_Area,
            r.Title, r.Author, r.Publisher, r.Description, r.Pub_Year, r.Genre,
            b.ISBN, pm.Page_Count, a.DOI, p.ISSN, p.Type AS Periodical_Type,
            v.Runtime, v.Format_Type, e.Type AS Equipment_Type,
            CASE WHEN b.Barcode IS NOT NULL THEN 'BOOK'
                 WHEN a.Barcode IS NOT NULL THEN 'ARTICLE'
                 WHEN p.Barcode IS NOT NULL THEN 'PERIODICAL'
                 WHEN v.Barcode IS NOT NULL THEN 'VIDEO_AUDIO'
                 ELSE 'EQUIPMENT' END AS media_type,
            EXISTS (SELECT 1 FROM Check_Out oc WHERE oc.ItemBarcode = i.Barcode
                    AND oc.Return_Date IS NULL AND oc.Due_Date < CURDATE()) AS is_overdue
            """;

    /** Joins that hang off an Item aliased as i. Equipment has no Record_Info row, hence all LEFT JOINs. */
    static final String ITEM_JOINS = """
            LEFT JOIN Record_Info r ON r.RecordID = i.RecordID
            LEFT JOIN Print_Media pm ON pm.Barcode = i.Barcode
            LEFT JOIN Book b ON b.Barcode = i.Barcode
            LEFT JOIN Article a ON a.Barcode = i.Barcode
            LEFT JOIN Periodical p ON p.Barcode = i.Barcode
            LEFT JOIN Video_Audio_Media v ON v.Barcode = i.Barcode
            LEFT JOIN Equipment e ON e.Barcode = i.Barcode
            """;

    private static final String BASE = "SELECT * FROM (SELECT " + ITEM_COLUMNS + " FROM Item i " + ITEM_JOINS + ") x";

    /** Turns the current row of a result set (selected with ITEM_COLUMNS) into a MediaItem. */
    static MediaItem mapItem(ResultSet rs) throws SQLException {
        MediaItem m = new MediaItem(rs.getString("Barcode"), MediaType.valueOf(rs.getString("media_type")));
        m.callNumber = rs.getString("Call_Number");
        m.condition = rs.getString("Condition");
        m.onlineRef = rs.getString("Online_Ref");
        m.floorNum = (Integer) rs.getObject("Floor_Num");
        m.collectionArea = rs.getString("Collection_Area");
        m.title = rs.getString("Title");
        m.author = rs.getString("Author");
        m.publisher = rs.getString("Publisher");
        m.description = rs.getString("Description");
        m.pubYear = (Integer) rs.getObject("Pub_Year");
        m.genre = rs.getString("Genre");
        m.isbn = rs.getString("ISBN");
        m.pageCount = (Integer) rs.getObject("Page_Count");
        m.doi = rs.getString("DOI");
        m.issn = rs.getString("ISSN");
        m.periodicalType = rs.getString("Periodical_Type");
        m.runtimeMinutes = (Integer) rs.getObject("Runtime");
        m.formatType = rs.getString("Format_Type");
        m.equipmentType = rs.getString("Equipment_Type");

        m.status = ItemStatus.AVAILABLE;
        String dbStatus = rs.getString("Status");
        for (ItemStatus s : ItemStatus.values()) {
            if (s.toString().equalsIgnoreCase(dbStatus)) {
                m.status = s;
            }
        }
        // Overdue is not stored on the item: it is an open checkout whose due date has passed.
        if (rs.getBoolean("is_overdue")) {
            m.status = ItemStatus.OVERDUE;
        }
        return m;
    }

    @Override
    public List<MediaItem> search(CatalogQuery q) {
        StringBuilder sql = new StringBuilder(BASE + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (q.mediaType() != null) {
            sql.append(" AND media_type = ?");
            params.add(q.mediaType().name());
        }
        if (q.genre() != null) {
            sql.append(" AND Genre = ?");
            params.add(q.genre());
        }
        if (q.availableOnly()) {
            sql.append(" AND `Status` = 'Available' AND is_overdue = 0");
        }
        if (q.text() != null && !q.text().isBlank()) {
            String like = "%" + q.text().trim() + "%";
            // Column names come from this fixed list, never from user input; the text itself is a ? parameter.
            String[] cols = switch (q.field()) {
                case TITLE -> new String[] { "Title", "Equipment_Type" };
                case AUTHOR -> new String[] { "Author" };
                case ID_NUMBER -> new String[] { "Barcode", "ISBN", "ISSN", "DOI", "Call_Number" };
                case ANY -> new String[] { "Title", "Author", "Genre", "Publisher", "Description", "Barcode",
                        "ISBN", "ISSN", "DOI", "Call_Number", "Equipment_Type", "media_type" };
            };
            sql.append(" AND (");
            for (int i = 0; i < cols.length; i++) {
                sql.append(i > 0 ? " OR " : "").append(cols[i]).append(" LIKE ?");
                params.add(like);
            }
            sql.append(")");
        }
        sql.append(" ORDER BY LOWER(COALESCE(Title, Equipment_Type, ''))");
        return query(sql.toString(), params);
    }

    @Override
    public MediaItem findByBarcode(String barcode) {
        List<MediaItem> found = query(BASE + " WHERE Barcode = ?", List.of(barcode));
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public List<String> genres() {
        List<String> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT DISTINCT Genre FROM Record_Info WHERE Genre IS NOT NULL AND Genre <> '' ORDER BY Genre")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        } catch (SQLException | java.io.IOException e) {
            throw new IllegalStateException("Could not load genres: " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public List<Location> locations() {
        List<Location> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT Floor_Num, Collection_Area FROM Location ORDER BY Floor_Num, Collection_Area")) {
            while (rs.next()) {
                out.add(new Location(rs.getInt(1), rs.getString(2)));
            }
        } catch (SQLException | java.io.IOException e) {
            throw new IllegalStateException("Could not load locations: " + e.getMessage(), e);
        }
        return out;
    }

    /**
     * Adds an item across the tables it lives in (Location, Record_Info, Item, then Print_Media + Book/Article/
     * Periodical, or Video_Audio_Media, or Equipment). One transaction: either every row is saved or none is.
     */
    @Override
    public void addItem(MediaItem m) {
        if (m.barcode == null || m.barcode.isBlank()) {
            throw new IllegalStateException("A barcode is required.");
        }
        if (m.floorNum == null || m.collectionArea == null || m.collectionArea.isBlank()) {
            throw new IllegalStateException("Choose a location.");
        }
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM Item WHERE Barcode = ?")) {
                    ps.setString(1, m.barcode);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            throw new IllegalStateException(
                                    "An item with barcode " + m.barcode + " already exists.");
                        }
                    }
                }

                // Item has a foreign key to Location, so the location row must exist first.
                run(c, "INSERT IGNORE INTO Location (Floor_Num, Collection_Area) VALUES (?, ?)",
                        m.floorNum, m.collectionArea);

                // Equipment has no Record_Info row. Everything else does; RecordID is not auto-numbered.
                Integer recordId = null;
                if (m.mediaType != MediaType.EQUIPMENT) {
                    try (Statement st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(RecordID), 0) + 1 FROM Record_Info")) {
                        rs.next();
                        recordId = rs.getInt(1);
                    }
                    run(c, "INSERT INTO Record_Info (RecordID, Author, Title, Publisher, Description, Pub_Year, Genre)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                            recordId, m.author, m.title, m.publisher, m.description, m.pubYear, m.genre);
                }

                run(c, "INSERT INTO Item (Barcode, Call_Number, RecordID, Floor_Num, Collection_Area, `Condition`, "
                        + "`Status`, Online_Ref) VALUES (?, ?, ?, ?, ?, ?, 'Available', ?)",
                        m.barcode, m.callNumber, recordId, m.floorNum, m.collectionArea, m.condition, m.onlineRef);

                switch (m.mediaType) {
                    case BOOK -> {
                        run(c, "INSERT INTO Print_Media (Barcode, Page_Count) VALUES (?, ?)", m.barcode, m.pageCount);
                        run(c, "INSERT INTO Book (Barcode, ISBN) VALUES (?, ?)", m.barcode, m.isbn);
                    }
                    case ARTICLE -> {
                        run(c, "INSERT INTO Print_Media (Barcode, Page_Count) VALUES (?, ?)", m.barcode, m.pageCount);
                        run(c, "INSERT INTO Article (Barcode, DOI) VALUES (?, ?)", m.barcode, m.doi);
                    }
                    case PERIODICAL -> {
                        run(c, "INSERT INTO Print_Media (Barcode, Page_Count) VALUES (?, ?)", m.barcode, m.pageCount);
                        run(c, "INSERT INTO Periodical (Barcode, ISSN, Type) VALUES (?, ?, ?)",
                                m.barcode, m.issn, m.periodicalType);
                    }
                    case VIDEO_AUDIO -> run(c,
                            "INSERT INTO Video_Audio_Media (Barcode, Runtime, Format_Type) VALUES (?, ?, ?)",
                            m.barcode, m.runtimeMinutes, m.formatType);
                    case EQUIPMENT -> run(c, "INSERT INTO Equipment (Barcode, Type) VALUES (?, ?)",
                            m.barcode, m.equipmentType);
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // duplicate key: someone added an item/record at the same moment
                throw new IllegalStateException("Another change happened at the same time. Please try again.", e);
            }
            throw new IllegalStateException("Could not add the item: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read config.properties: " + e.getMessage(), e);
        }
        m.status = ItemStatus.AVAILABLE;
    }

    /** Runs one INSERT with the given parameters (null becomes SQL NULL). */
    private static void run(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private List<MediaItem> query(String sql, List<Object> params) {
        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<MediaItem> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapItem(rs));
                }
                return out;
            }
        } catch (SQLException | java.io.IOException e) {
            throw new IllegalStateException("Catalog query failed: " + e.getMessage(), e);
        }
    }
}
