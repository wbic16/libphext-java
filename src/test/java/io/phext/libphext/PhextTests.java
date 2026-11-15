package io.phext.libphext;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of libphext-rs {@code src/test_lib.rs}. Adaptations, all mechanical:
 * Rust {@code usize} is Java {@code long} (so the "largest component" case
 * uses {@link Long#MAX_VALUE}); the fs test writes under the harness temp
 * directory instead of the working tree; the two performance tests keep
 * their structural assertions and Rust's time budgets.
 */
public final class PhextTests {
    private PhextTests() {
    }

    private static Coordinate coord(String address) {
        return Phext.toCoordinate(address);
    }

    private static String rep(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    public static void register() {
        TestMain.test("PhextTests", "test_coordinate_parsing", () -> {
            Coordinate test = Phext.toCoordinate("9.8.7/6.5.4/3.2.1");
            A.eq("9.8.7/6.5.4/3.2.1", test.toString(), "Coordinate parsing failed");
            A.eq("1.1.1/1.1.1/1.1.1", Phext.toCoordinate("HOME").toString());
        });

        TestMain.test("PhextTests", "test_to_urlencoded", () -> {
            A.eq("1.1.1;1.1.1;1.1.1", coord("1.1.1/1.1.1/1.1.1").toUrlencoded());
            A.eq("98.76.54;32.10.1;23.45.67", coord("98.76.54/32.10.1/23.45.67").toUrlencoded());
        });

        TestMain.test("PhextTests", "test_scrolls", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Hello World", "1.1.1/1.1.1/1.1.1");
            data.put("Scroll #2 -- this text will be selected", "1.1.1/1.1.1/1.1.2");
            data.put("Scroll #3 - this text will be ignored", "1.1.1/1.1.1/1.1.3");
            A.isTrue(testHelper(Phext.SCROLL_BREAK, data));
        });

        TestMain.test("PhextTests", "test_sections", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Section A", "1.1.1/1.1.1/1.1.1");
            data.put("Section B", "1.1.1/1.1.1/1.2.1");
            data.put("Section C", "1.1.1/1.1.1/1.3.1");
            A.isTrue(testHelper(Phext.SECTION_BREAK, data));
        });

        TestMain.test("PhextTests", "test_chapters", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Chapter Alpha", "1.1.1/1.1.1/1.1.1");
            data.put("Chapter Beta", "1.1.1/1.1.1/2.1.1");
            data.put("Chapter Gamma", "1.1.1/1.1.1/3.1.1");
            A.isTrue(testHelper(Phext.CHAPTER_BREAK, data));
        });

        TestMain.test("PhextTests", "test_books", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Book z1", "1.1.1/1.1.1/1.1.1");
            data.put("Book Something Else #2", "1.1.1/1.1.2/1.1.1");
            data.put("Book Part 3", "1.1.1/1.1.3/1.1.1");
            A.isTrue(testHelper(Phext.BOOK_BREAK, data));
        });

        TestMain.test("PhextTests", "test_volumes", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Volume 1-1-1", "1.1.1/1.1.1/1.1.1");
            data.put("Volume 1-2-1", "1.1.1/1.2.1/1.1.1");
            data.put("Volume 1-3-1", "1.1.1/1.3.1/1.1.1");
            A.isTrue(testHelper(Phext.VOLUME_BREAK, data));
        });

        TestMain.test("PhextTests", "test_collections", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Collection 1-1-1", "1.1.1/1.1.1/1.1.1");
            data.put("Collection 2-1-1", "1.1.1/2.1.1/1.1.1");
            data.put("Collection 3-1-1", "1.1.1/3.1.1/1.1.1");
            A.isTrue(testHelper(Phext.COLLECTION_BREAK, data));
        });

        TestMain.test("PhextTests", "test_series", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Series 1-1-1", "1.1.1/1.1.1/1.1.1");
            data.put("Series 1-1-2", "1.1.2/1.1.1/1.1.1");
            data.put("Series 1-1-3", "1.1.3/1.1.1/1.1.1");
            A.isTrue(testHelper(Phext.SERIES_BREAK, data));
        });

        TestMain.test("PhextTests", "test_shelves", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Shelf 1-1-1", "1.1.1/1.1.1/1.1.1");
            data.put("Shelf 1-2-1", "1.2.1/1.1.1/1.1.1");
            data.put("Shelf 1-3-1", "1.3.1/1.1.1/1.1.1");
            A.isTrue(testHelper(Phext.SHELF_BREAK, data));
        });

        TestMain.test("PhextTests", "test_libraries", () -> {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("Library 1-1-1", "1.1.1/1.1.1/1.1.1");
            data.put("Library 2-1-1", "2.1.1/1.1.1/1.1.1");
            data.put("Library 3-1-1", "3.1.1/1.1.1/1.1.1");
            A.isTrue(testHelper(Phext.LIBRARY_BREAK, data));
        });

        TestMain.test("PhextTests", "test_coordinates_invalid", () -> {
            Coordinate c1 = Phext.toCoordinate("0.0.0/0.0.0/0.0.0");
            Coordinate c2 = new Coordinate(
                new ZCoordinate(0, 0, 0), new YCoordinate(0, 0, 0), new XCoordinate(0, 0, 0));
            A.eq(c2, c1);
            A.isFalse(c1.validateCoordinate());
            A.isFalse(c2.validateCoordinate());
        });

        TestMain.test("PhextTests", "test_coordinates_valid", () -> {
            Coordinate c1 = Phext.toCoordinate("1001.254.253/32.4.8/4.2.1");
            Coordinate c2 = new Coordinate(
                new ZCoordinate(1001, 254, 253), new YCoordinate(32, 4, 8), new XCoordinate(4, 2, 1));
            A.eq(c2, c1);
            A.eq(4, c1.y.volume);
            A.isTrue(c1.validateCoordinate());
            A.isTrue(c2.validateCoordinate());
        });

        TestMain.test("PhextTests", "test_url_encoding", () -> {
            Coordinate c1 = Phext.toCoordinate("142.143.144;145.146.147;148.149.150");
            Coordinate c2 = new Coordinate(
                new ZCoordinate(142, 143, 144), new YCoordinate(145, 146, 147), new XCoordinate(148, 149, 150));
            A.eq(c2, c1);
            A.isTrue(c1.validateCoordinate());
            A.isTrue(c2.validateCoordinate());
            Coordinate c3 = Phext.toCoordinate("1001.1002.1003;1004.1005.1006;1007.1008.1009");
            Coordinate c4 = new Coordinate(
                new ZCoordinate(1001, 1002, 1003), new YCoordinate(1004, 1005, 1006), new XCoordinate(1007, 1008, 1009));
            A.eq(c4, c3);
            A.isTrue(c3.validateCoordinate());
            A.isTrue(c4.validateCoordinate());
        });

        TestMain.test("PhextTests", "test_realistic_parse", () -> {
            Coordinate coord = Phext.toCoordinate("6.13.4/2.11.4/2.20.3");
            // break runs counted from the Rust test's literal:
            // 5 library, 12 shelf, 3 series, 1 collection, 10 volume,
            // 3 book, 1 chapter, 19 section, 2 scroll
            String subspace = rep('\u0001', 5) + rep('\u001F', 12) + rep('\u001E', 3)
                + rep('\u001D', 1) + rep('\u001C', 10) + rep('\u001A', 3) + rep('\u0019', 1)
                + rep('\u0018', 19) + rep('\u0017', 2)
                + "here's some text at 6.13.4/2.11.4/2.20.3"
                + "\u0017this is the next scroll and won't be picked";
            A.eq("here's some text at 6.13.4/2.11.4/2.20.3", Phext.fetch(subspace, coord));
        });

        TestMain.test("PhextTests", "test_dead_reckoning", () -> {
            StringBuilder test = new StringBuilder();
            test.append("random text in 1.1.1/1.1.1/1.1.1 that we can skip past");
            test.append(Phext.LIBRARY_BREAK);
            test.append("everything in here is at 2.1.1/1.1.1/1.1.1");
            test.append(Phext.SCROLL_BREAK);
            test.append("and now we're at 2.1.1/1.1.1/1.1.2");
            test.append(Phext.SCROLL_BREAK);
            test.append("moving on up to 2.1.1/1.1.1/1.1.3");
            test.append(Phext.BOOK_BREAK);
            test.append("and now over to 2.1.1/1.1.2/1.1.1");
            test.append(Phext.SHELF_BREAK);
            test.append("woot, up to 2.2.1/1.1.1/1.1.1");
            test.append(Phext.LIBRARY_BREAK);
            test.append("here we are at 3.1.1/1.1.1.1.1");
            test.append(Phext.LIBRARY_BREAK);
            test.append(Phext.LIBRARY_BREAK);
            test.append("getting closer to our target now 5.1.1/1.1.1/1.1.1");
            for (int i = 0; i < 4; i++) test.append(Phext.SHELF_BREAK);      // 5.5.1
            for (int i = 0; i < 4; i++) test.append(Phext.SERIES_BREAK);     // 5.5.5
            test.append("here we go! 5.5.5/1.1.1/1.1.1");
            for (int i = 0; i < 3; i++) test.append(Phext.COLLECTION_BREAK); // 5.5.5/4.1.1
            for (int i = 0; i < 3; i++) test.append(Phext.BOOK_BREAK);       // 5.5.5/4.1.4
            test.append("this test appears at 5.5.5/4.1.4/1.1.1");
            for (int i = 0; i < 5; i++) test.append(Phext.VOLUME_BREAK);     // 5.5.5/4.6.1
            for (int i = 0; i < 4; i++) test.append(Phext.CHAPTER_BREAK);    // 5.5.5/4.6.1/5.1.1
            for (int i = 0; i < 6; i++) test.append(Phext.BOOK_BREAK);       // 5.5.5/4.6.7
            for (int i = 0; i < 4; i++) test.append(Phext.CHAPTER_BREAK);    // 5.5.5/4.6.7/5.1.1
            for (int i = 0; i < 5; i++) test.append(Phext.SCROLL_BREAK);     // 5.5.5/4.6.7/5.1.6
            test.append("here's a test at 5.5.5/4.6.7/5.1.6");
            test.append(Phext.SCROLL_BREAK);
            for (int i = 0; i < 4; i++) test.append(Phext.CHAPTER_BREAK);    // 5.5.5/4.6.7/9.1.1
            for (int i = 0; i < 4; i++) test.append(Phext.SECTION_BREAK);    // 5.5.5/4.6.7/9.5.1
            for (int i = 0; i < 8; i++) test.append(Phext.SCROLL_BREAK);     // 5.5.5/4.6.7/9.5.9
            test.append("Expected Test Pattern Alpha Whisky Tango Foxtrot");
            String doc = test.toString();
            A.eq("Expected Test Pattern Alpha Whisky Tango Foxtrot",
                Phext.fetch(doc, coord("5.5.5/4.6.7/9.5.9")));
            A.eq("here's a test at 5.5.5/4.6.7/5.1.6",
                Phext.fetch(doc, coord("5.5.5/4.6.7/5.1.6")));
        });

        TestMain.test("PhextTests", "test_line_break", () -> {
            A.eq('\n', Phext.LINE_BREAK);
        });

        TestMain.test("PhextTests", "test_more_cowbell", () -> {
            A.eq('\u0007', Phext.MORE_COWBELL);
            A.isTrue(Phext.checkForCowbell("Hello\u0007"));
            A.isFalse(Phext.checkForCowbell("nope\u0017just more scrolls"));
        });
    }

    private static boolean testHelper(char delim, Map<String, String> data) {
        if (data.size() < 3) {
            return false;
        }
        String[] expects = data.keySet().toArray(new String[0]);
        String sample = expects[0] + delim + expects[1] + delim + expects[2];
        for (int i = 0; i < 3; i++) {
            String text = Phext.fetch(sample, Phext.toCoordinate(data.get(expects[i])));
            A.eq(expects[i], text, "Fetching text for coord " + (i + 1) + " failed");
        }
        return true;
    }

    public static void registerPart2() {
        TestMain.test("PhextTests", "test_coordinate_based_insert", () -> {
            String test = "aaa" + "\u0001" + "bbb" + "\u0017" + "ccc";
            Coordinate root = coord("1.1.1/1.1.1/1.1.1");
            Coordinate coord1 = coord("2.1.1/1.1.1/1.1.3");
            Phext.SubspaceCoordinates expected1 = Phext.getSubspaceCoordinates(
                test.getBytes(java.nio.charset.StandardCharsets.UTF_8), coord1);
            A.eq(2, expected1.best().z.library);
            A.eq(1, expected1.best().z.shelf);
            A.eq(1, expected1.best().z.series);
            A.eq(1, expected1.best().y.collection);
            A.eq(1, expected1.best().y.volume);
            A.eq(1, expected1.best().y.book);
            A.eq(1, expected1.best().x.chapter);
            A.eq(1, expected1.best().x.section);
            A.eq(2, expected1.best().x.scroll);
            A.eq(11, expected1.start());
            A.eq(11, expected1.end());
            Coordinate expectedCoord = Phext.defaultCoordinate();
            expectedCoord.z.library = 2;
            expectedCoord.x.scroll = 3;
            A.eq(expectedCoord, coord1);

            String u = Phext.insert(test, coord1, "ddd");
            A.eq("aaa\u0001bbb\u0017ccc\u0017ddd", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.1.4"), "eee");
            A.eq("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.2.1"), "fff");
            A.eq("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0018fff", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.2.2"), "ggg");
            A.eq("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0018fff\u0017ggg", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/2.1.1"), "hhh");
            A.eq("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.1.5"), "iii");
            A.eq("aaa\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, root, "---AAA");
            A.eq("aaa---AAA\u0001bbb\u0017ccc\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.1.1"), "---BBB");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.1.2"), "---CCC");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.1.3"), "---DDD");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee\u0017iii\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.1.4"), "---EEE");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.1.5"), "---III");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.2.1"), "---FFF");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/1.2.2"), "---GGG");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh", u);
            u = Phext.insert(u, coord("2.1.1/1.1.1/2.1.1"), "---HHH");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH", u);
            u = Phext.insert(u, coord("2.1.1/1.1.2/1.1.1"), "jjj");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj", u);
            u = Phext.insert(u, coord("2.1.1/1.2.1/1.1.1"), "kkk");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk", u);
            u = Phext.insert(u, coord("2.1.1/2.1.1/1.1.1"), "lll");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll", u);
            u = Phext.insert(u, coord("2.1.2/1.1.1/1.1.1"), "mmm");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll\u001Emmm", u);
            u = Phext.insert(u, coord("2.2.1/1.1.1/1.1.1"), "nnn");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll\u001Emmm\u001Fnnn", u);
            u = Phext.insert(u, coord("3.1.1/1.1.1/1.1.1"), "ooo");
            A.eq("aaa---AAA\u0001bbb---BBB\u0017ccc---CCC\u0017ddd---DDD\u0017eee---EEE\u0017iii---III\u0018fff---FFF\u0017ggg---GGG\u0019hhh---HHH\u001Ajjj\u001Ckkk\u001Dlll\u001Emmm\u001Fnnn\u0001ooo", u);
        });

        TestMain.test("PhextTests", "test_coordinate_based_replace", () -> {
            String u = Phext.replace("AAA\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", coord("1.1.1/1.1.1/1.1.1"), "aaa");
            A.eq("aaa\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.1.1/1.1.1/1.1.2"), "222");
            A.eq("aaa\u0017222\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.1.1/1.1.1/1.2.1"), "3-");
            A.eq("aaa\u0017222\u00183-\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.1.1/1.1.1/2.1.1"), "delta");
            A.eq("aaa\u0017222\u00183-\u0019delta\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.1.1/1.1.2/1.1.1"), "a bridge just close enough");
            A.eq("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.1.1/1.2.1/1.1.1"), "nifty");
            A.eq("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.1.1/2.1.1/1.1.1"), "G8");
            A.eq("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.1.2/1.1.1/1.1.1"), "Hello World");
            A.eq("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001EHello World\u001Fiii\u0001jjj", u);
            u = Phext.replace(u, coord("1.2.1/1.1.1/1.1.1"), "_o_");
            A.eq("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001EHello World\u001F_o_\u0001jjj", u);
            u = Phext.replace(u, coord("2.1.1/1.1.1/1.1.1"), "/win");
            A.eq("aaa\u0017222\u00183-\u0019delta\u001Aa bridge just close enough\u001Cnifty\u001DG8\u001EHello World\u001F_o_\u0001/win", u);

            String r0a = Phext.replace("hello world\u0017scroll two", coord("2.1.1/1.1.1/1.1.5"), "2.1.1-1.1.1-1.1.5");
            A.eq("hello world\u0017scroll two\u0001\u0017\u0017\u0017\u00172.1.1-1.1.1-1.1.5", r0a);

            String r1 = Phext.replace("", coord("1.1.1/1.1.1/1.1.1"), "aaa");
            A.eq("aaa", r1);
            r1 = Phext.replace(r1, coord("1.1.1/1.1.1/1.1.2"), "bbb");
            A.eq("aaa\u0017bbb", r1);
            r1 = Phext.replace(r1, coord("1.2.3/4.5.6/7.8.9"), "ccc");
            A.eq("aaa\u0017bbb\u001F\u001E\u001E\u001D\u001D\u001D\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ccc", r1);
            r1 = Phext.replace(r1, coord("1.4.4/2.8.8/4.16.16"), "ddd");
            A.eq("aaa\u0017bbb\u001F\u001E\u001E\u001D\u001D\u001D\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ccc\u001F\u001F\u001E\u001E\u001E\u001D\u001C\u001C\u001C\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017\u0017ddd", r1);
            String r1Before = r1;
            r1 = Phext.replace(r1, coord("11.12.13/14.15.16/17.18.19"), "eee");
            A.eq(r1Before
                + rep('\u0001', 10) + rep('\u001F', 11) + rep('\u001E', 12) + rep('\u001D', 13)
                + rep('\u001C', 14) + rep('\u001A', 15) + rep('\u0019', 16) + rep('\u0018', 17)
                + rep('\u0017', 18) + "eee", r1);

            String baseline = "1.1.1\u00171.1.2\u00171.1.3\u00171.1.4\u00181.2.1\u00171.2.2\u00171.2.3\u00171.2.4\u0019"
                + "2.1.1\u00193.1.1\u00194.1.1\u001a2/1.1.1\u0017\u00172/1.1.3\u001c2.1/1.1.1\u001d2.1.1/1.1.1\u001e2/1.1.1/1.1.1\u001f2.1/1.1.1/1.1.1\u00012.1.1/1.1.1/1.1.1";
            String r2 = Phext.replace(baseline, coord("1.1.1/1.1.2/1.1.2"), "new content");
            A.eq("1.1.1\u00171.1.2\u00171.1.3\u00171.1.4\u00181.2.1\u00171.2.2\u00171.2.3\u00171.2.4\u00192.1.1\u00193.1.1\u00194.1.1\u001a2/1.1.1\u0017new content\u00172/1.1.3\u001c2.1/1.1.1\u001d2.1.1/1.1.1\u001e2/1.1.1/1.1.1\u001f2.1/1.1.1/1.1.1\u00012.1.1/1.1.1/1.1.1", r2);
        });

        TestMain.test("PhextTests", "test_coordinate_based_remove", () -> {
            String u = Phext.remove("aaa\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", coord("1.1.1/1.1.1/1.1.1"));
            A.eq("\u0017bbb\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.1.1/1.1.1/1.1.2"));
            A.eq("\u0018ccc\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.1.1/1.1.1/1.2.1"));
            A.eq("\u0019ddd\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.1.1/1.1.1/2.1.1"));
            A.eq("\u001Aeee\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.1.1/1.1.2/1.1.1"));
            A.eq("\u001Cfff\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.1.1/1.2.1/1.1.1"));
            A.eq("\u001Dggg\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.1.1/2.1.1/1.1.1"));
            A.eq("\u001Ehhh\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.1.2/1.1.1/1.1.1"));
            A.eq("\u001Fiii\u0001jjj", u);
            u = Phext.remove(u, coord("1.2.1/1.1.1/1.1.1"));
            A.eq("\u0001jjj", u);
            u = Phext.remove(u, coord("2.1.1/1.1.1/1.1.1"));
            A.eq("", u);
        });

        TestMain.test("PhextTests", "test_range_based_replace", () -> {
            String doc1 = "Before\u0019text to be replaced\u001Calso this\u001Dand this\u0017After";
            Range range1 = new Range(coord("1.1.1/1.1.1/2.1.1"), coord("1.1.1/2.1.1/1.1.1"));
            A.eq("Before\u0019\u0017After", Phext.rangeReplace(doc1, range1, ""));
            String doc2 = "Before\u0001Library two\u0001Library three\u0001Library four";
            Range range2 = new Range(coord("2.1.1/1.1.1/1.1.1"), coord("3.1.1/1.1.1/1.1.1"));
            A.eq("Before\u0001\u0001Library four", Phext.rangeReplace(doc2, range2, ""));
        });

        TestMain.test("PhextTests", "test_next_scroll", () -> {
            Phext.NextScrollResult r = Phext.nextScroll("3A\u0017B2\u0018C1", coord("1.1.1/1.1.1/1.1.1"));
            A.eq("1.1.1/1.1.1/1.1.1", r.scroll().coord.toString());
            A.eq("3A", r.scroll().scroll);
            A.eq("1.1.1/1.1.1/1.1.2", r.next().toString());
            A.eq("B2\u0018C1", r.remaining());
        });

        TestMain.test("PhextTests", "test_phokenize", () -> {
            A.eq(List.of(
                new PositionedScroll(coord("1.1.1/1.1.1/1.1.1"), "one"),
                new PositionedScroll(coord("1.1.1/1.1.1/1.1.2"), "two"),
                new PositionedScroll(coord("1.1.1/1.1.1/1.1.3"), "three"),
                new PositionedScroll(coord("1.1.1/1.1.1/1.1.4"), "four")),
                Phext.phokenize("one\u0017two\u0017three\u0017four"));
            A.eq(List.of(
                new PositionedScroll(coord("1.1.1/1.1.1/1.1.1"), "one"),
                new PositionedScroll(coord("2.1.1/1.1.1/1.1.1"), "two"),
                new PositionedScroll(coord("2.2.1/1.1.1/1.1.1"), "three"),
                new PositionedScroll(coord("2.2.2/1.1.1/1.1.1"), "four"),
                new PositionedScroll(coord("2.2.2/2.1.1/1.1.1"), "five"),
                new PositionedScroll(coord("2.2.2/2.2.1/1.1.1"), "six"),
                new PositionedScroll(coord("2.2.2/2.2.2/1.1.1"), "seven"),
                new PositionedScroll(coord("2.2.2/2.2.2/2.1.1"), "eight"),
                new PositionedScroll(coord("2.2.2/2.2.2/2.2.1"), "nine"),
                new PositionedScroll(coord("2.2.2/2.2.2/2.2.2"), "ten")),
                Phext.phokenize("one\u0001two\u001Fthree\u001Efour\u001Dfive\u001Csix\u001Aseven\u0019eight\u0018nine\u0017ten"));
            A.eq(List.of(
                new PositionedScroll(coord("1.1.1/1.1.1/1.1.1"), "one"),
                new PositionedScroll(coord("1.1.1/1.1.1/1.1.2"), "two"),
                new PositionedScroll(coord("1.1.1/1.1.1/1.2.1"), "three"),
                new PositionedScroll(coord("1.1.1/1.1.1/2.1.1"), "four"),
                new PositionedScroll(coord("1.1.1/1.1.2/1.1.1"), "five"),
                new PositionedScroll(coord("1.1.1/1.2.1/1.1.1"), "six"),
                new PositionedScroll(coord("1.1.1/2.1.1/1.1.1"), "seven"),
                new PositionedScroll(coord("1.1.2/1.1.1/1.1.1"), "eight"),
                new PositionedScroll(coord("1.2.1/1.1.1/1.1.1"), "nine"),
                new PositionedScroll(coord("2.1.1/1.1.1/1.1.1"), "ten")),
                Phext.phokenize("one\u0017two\u0018three\u0019four\u001Afive\u001Csix\u001Dseven\u001Eeight\u001Fnine\u0001ten"));
            A.eq(List.of(new PositionedScroll(coord("2.1.1/1.1.1/1.1.1"), "stuff here")),
                Phext.phokenize("\u001A\u001C\u001D\u001E\u001F\u0001stuff here"));
        });

        TestMain.test("PhextTests", "test_last_empty_scroll", () -> {
            String doc1 = "hello\u0017world\u0017";
            Coordinate target1 = coord("1.1.1/1.1.1/1.1.2");
            Phext.SubspaceCoordinates parts1 = Phext.getSubspaceCoordinates(
                doc1.getBytes(java.nio.charset.StandardCharsets.UTF_8), target1);
            A.eq(6, parts1.start());
            A.eq(11, parts1.end());
            A.eq(target1, parts1.best());
            A.eq("world", Phext.fetch(doc1, target1));
        });

        TestMain.test("PhextTests", "test_merge", () -> {
            A.eq("3A4C\u0017B2D1", Phext.merge("3A\u0017B2", "4C\u0017D1"));
            A.eq("Hello Darkness, my old friend.\u0017I've come to talk with you again.",
                Phext.merge("Hello \u0017I've come to talk", "Darkness, my old friend.\u0017 with you again."));
            A.eq("One1\u0017Two2\u0018Three3\u0019Four4",
                Phext.merge("One\u0017Two\u0018Three\u0019Four", "1\u00172\u00183\u00194"));
            A.eq("\u001Eprecursor here\u0001stuff hereand more",
                Phext.merge("\u001A\u001C\u001D\u001E\u001F\u0001stuff here", "\u001A\u001C\u001D\u001Eprecursor here\u001F\u0001and more"));
            A.eq("\u0001\u0001 Library at 3.1.1/1.1.1/1.1.1 \u001F Shelf at 3.2.1/1.1.1/1.1.1\u0001 Library 4.1.1/1.1.1/1.1.1 \u001E Series at 4.1.2/1.1.1/1.1.1",
                Phext.merge("\u0001\u0001 Library at 3.1.1/1.1.1/1.1.1 \u001F Shelf at 3.2.1/1.1.1/1.1.1",
                            "\u0001\u0001\u0001 Library 4.1.1/1.1.1/1.1.1 \u001E Series at 4.1.2/1.1.1/1.1.1"));
            A.eq("\u001D Collection at 1.1.1/2.1.1/1.1.1\u001C Volume at 1.1.1/2.2.1/1.1.1\u001D Collection at 1.1.1/3.1.1/1.1.1\u001C Volume at 1.1.1/3.2.1/1.1.1",
                Phext.merge("\u001D Collection at 1.1.1/2.1.1/1.1.1\u001C Volume at 1.1.1/2.2.1/1.1.1",
                            "\u001D\u001D Collection at 1.1.1/3.1.1/1.1.1\u001C Volume at 1.1.1/3.2.1/1.1.1"));
            A.eq("\u001ABook #2 Part 1 + Part II\u001ABook #3 Part 1 + Part Deux",
                Phext.merge("\u001ABook #2 Part 1\u001ABook #3 Part 1", "\u001A + Part II\u001A + Part Deux"));
            A.eq("AA__\u0001BB__\u0001CC__", Phext.merge("AA\u0001BB\u0001CC", "__\u0001__\u0001__"));
        });

        TestMain.test("PhextTests", "test_subtract", () -> {
            A.eq("\u0017Scroll two.", Phext.subtract("Here's scroll one.\u0017Scroll two.", "Just content at the first scroll"));
        });

        TestMain.test("PhextTests", "test_normalize", () -> {
            A.eq("\u0017Scroll two", Phext.normalize("\u0017Scroll two\u0018\u0018\u0018\u0018"));
        });

        TestMain.test("PhextTests", "test_expand", () -> {
            String u = "nothing but line breaks\nto test expansion to scrolls\nline 3";
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u0017to test expansion to scrolls\u0017line 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u0018to test expansion to scrolls\u0018line 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u0019to test expansion to scrolls\u0019line 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u001Ato test expansion to scrolls\u001Aline 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u001Cto test expansion to scrolls\u001Cline 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u001Dto test expansion to scrolls\u001Dline 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u001Eto test expansion to scrolls\u001Eline 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u001Fto test expansion to scrolls\u001Fline 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u0001to test expansion to scrolls\u0001line 3", u);
            u = Phext.expand(u);
            A.eq("nothing but line breaks\u0001to test expansion to scrolls\u0001line 3", u);
        });

        TestMain.test("PhextTests", "test_contract", () -> {
            String u = Phext.contract("A more complex example than expand\u0001----\u001F++++\u001E____\u001Doooo\u001C====\u001Azzzz\u0019gggg\u0018....\u0017qqqq");
            A.eq("A more complex example than expand\u001F----\u001E++++\u001D____\u001Coooo\u001A====\u0019zzzz\u0018gggg\u0017....\nqqqq", u);
            u = Phext.contract(u);
            A.eq("A more complex example than expand\u001E----\u001D++++\u001C____\u001Aoooo\u0019====\u0018zzzz\u0017gggg\n....\nqqqq", u);
        });

        TestMain.test("PhextTests", "test_fs_read_write", () -> {
            java.nio.file.Path file = Files.createTempFile("unit-test", ".phext");
            String initial = "a simple phext doc with three scrolls\u0017we just want to verify\u0017that all of our breaks are making it through rust's fs layer.\u0018section 2\u0019chapter 2\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001library 2";
            Files.writeString(file, initial, java.nio.charset.StandardCharsets.UTF_8);
            String prior = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            A.eq(initial, prior);
            String message = Phext.replace(prior, coord("2.1.1/1.1.1/1.1.1"), "still lib 2");
            A.eq("a simple phext doc with three scrolls\u0017we just want to verify\u0017that all of our breaks are making it through rust's fs layer.\u0018section 2\u0019chapter 2\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001still lib 2", message);
            Files.deleteIfExists(file);
        });

        TestMain.test("PhextTests", "test_replace_create", () -> {
            String message = Phext.replace("A\u0017B\u0017C\u0018D\u0019E\u001AF\u001CG\u001DH\u001EI\u001FJ\u0001K", coord("3.1.1/1.1.1/1.1.1"), "L");
            A.eq("A\u0017B\u0017C\u0018D\u0019E\u001AF\u001CG\u001DH\u001EI\u001FJ\u0001K\u0001L", message);
        });

        TestMain.test("PhextTests", "test_summary", () -> {
            A.eq("A short phext...", Phext.createSummary("A short phext\nSecond line\u0017second scroll............................."));
            A.eq("very terse", Phext.createSummary("very terse"));
        });

        TestMain.test("PhextTests", "test_navmap", () -> {
            String result = Phext.navmap("http://127.0.0.1/api/v1/index/", "Just a couple of scrolls.\u0017Second scroll\u0017Third scroll");
            A.eq("<ul>\n<li><a href=\"http://127.0.0.1/api/v1/index/1.1.1;1.1.1;1.1.1\">1.1.1/1.1.1/1.1.1 Just a couple of scrolls.</a></li>\n<li><a href=\"http://127.0.0.1/api/v1/index/1.1.1;1.1.1;1.1.2\">1.1.1/1.1.1/1.1.2 Second scroll</a></li>\n<li><a href=\"http://127.0.0.1/api/v1/index/1.1.1;1.1.1;1.1.3\">1.1.1/1.1.1/1.1.3 Third scroll</a></li>\n</ul>\n", result);
        });

        TestMain.test("PhextTests", "test_textmap", () -> {
            A.eq("* 1.1.1/1.1.1/1.1.1: Just a couple of scrolls.\n* 1.1.1/1.1.1/1.1.2: Second scroll\n* 1.1.1/1.1.1/1.1.3: Third scroll\n",
                Phext.textmap("Just a couple of scrolls.\u0017Second scroll\u0017Third scroll"));
        });

        TestMain.test("PhextTests", "test_larger_coordinates", () -> {
            Coordinate coord = coord("111.222.333/444.555.666/777.888.999");
            String result = Phext.insert("", coord, "Hello World");
            A.eq(4997, result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            A.eq("* 111.222.333/444.555.666/777.888.999: Hello World\n", Phext.textmap(result));
        });

        TestMain.test("PhextTests", "test_phext_index", () -> {
            String example = "first scroll\u0017second scroll\u0018second section\u0019second chapter\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001library 2";
            A.eq("0\u001713\u001827\u001942\u001A57\u001C64\u001D73\u001E86\u001F95\u0001103", Phext.index(example));
            A.eq(0, Phext.offset(example, coord("1.1.1/1.1.1/1.1.1")));
            A.eq(13, Phext.offset(example, coord("1.1.1/1.1.1/1.1.2")));
            A.eq(27, Phext.offset(example, coord("1.1.1/1.1.1/1.2.1")));
            A.eq(42, Phext.offset(example, coord("1.1.1/1.1.1/2.1.1")));
            A.eq(57, Phext.offset(example, coord("1.1.1/1.1.2/1.1.1")));
            A.eq(64, Phext.offset(example, coord("1.1.1/1.2.1/1.1.1")));
            A.eq(73, Phext.offset(example, coord("1.1.1/2.1.1/1.1.1")));
            A.eq(86, Phext.offset(example, coord("1.1.2/1.1.1/1.1.1")));
            A.eq(95, Phext.offset(example, coord("1.2.1/1.1.1/1.1.1")));
            A.eq(103, Phext.offset(example, coord("2.1.1/1.1.1/1.1.1")));
            A.eq(103, Phext.offset(example, coord("2.1.1/1.1.1/1.2.1")));
            A.eq(112, example.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        });

        TestMain.test("PhextTests", "test_scroll_manifest", () -> {
            String example = "first scroll\u0017second scroll\u0018second section\u0019second chapter\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001library 2";
            String result = Phext.manifest(example);
            A.eq("7e79edd92a62a048e1cd24ffab542e34", Phext.checksum("00000000000000000000"));
            A.eq("ba9d944e4967e29d48bae69ac2999699", Phext.checksum("first scroll"));
            A.eq("2fe1b2040314ac66f132dd3b4926157c", Phext.checksum("second scroll"));
            A.eq("fddb6916753b6f4e0b5281469134778b", Phext.checksum("second section"));
            A.eq("16ab5b1a0a997db95ec215a3bf2c57b3", Phext.checksum("second chapter"));
            A.eq("0f20f79bf36f63e8fba25cc6765e2d0d", Phext.checksum("book 2"));
            A.eq("7ead0c6fef43adb446fe3bda6fb0adc7", Phext.checksum("volume 2"));
            A.eq("78c12298931c6edede92962137a9280a", Phext.checksum("collection 2"));
            A.eq("0f35100c84df601a490b7b63d7e8c0a8", Phext.checksum("series 2"));
            A.eq("3bbf7e67cb33d613a906bc5a3cbefd95", Phext.checksum("shelf 2"));
            A.eq("2e7fdd387196a8a2706ccb9ad6792bc3", Phext.checksum("library 2"));
            String expected = Phext.checksum("first scroll") + "\u0017" + Phext.checksum("second scroll")
                + "\u0018" + Phext.checksum("second section") + "\u0019" + Phext.checksum("second chapter")
                + "\u001A" + Phext.checksum("book 2") + "\u001C" + Phext.checksum("volume 2")
                + "\u001D" + Phext.checksum("collection 2") + "\u001E" + Phext.checksum("series 2")
                + "\u001F" + Phext.checksum("shelf 2") + "\u0001" + Phext.checksum("library 2");
            A.eq(expected, result);
        });

        TestMain.test("PhextTests", "test_phext_soundex_v1", () -> {
            String sample = "it was the best of scrolls\u0017it was the worst of scrolls\u0017aaa\u0017bbb\u0017ccc\u0017ddd\u0017eee\u0017fff\u0017ggg\u0017hhh\u0017iii\u0017jjj\u0017kkk\u0017lll\u0018mmm\u0018nnn\u0018ooo\u0018ppp\u0019qqq\u0019rrr\u0019sss\u0019ttt\u001Auuu\u001Avvv\u001Awww\u001Axxx\u001Ayyy\u001Azzz";
            A.eq("36\u001741\u00171\u00174\u00177\u001710\u00171\u00174\u00177\u00171\u00171\u00177\u00177\u001713\u001816\u001816\u00181\u00184\u00197\u001919\u00197\u001910\u001A1\u001A4\u001A1\u001A7\u001A1\u001A7", Phext.soundexV1(sample));
        });

        TestMain.test("PhextTests", "test_new_operators", () -> {
            PositionedScroll ps = new PositionedScroll();
            A.eq("", ps.scroll);
            A.eq(coord("1.1.1/1.1.1/1.1.1"), ps.coord);
            Coordinate c = new Coordinate();
            A.eq(new ZCoordinate(), c.z);
            A.eq(new YCoordinate(), c.y);
            A.eq(new XCoordinate(), c.x);
            Range range = new Range();
            A.eq(new Coordinate(), range.start);
            A.eq(new Coordinate(), range.end);
            A.eq(1, new ZCoordinate().library);
            A.eq(1, new ZCoordinate().shelf);
            A.eq(1, new ZCoordinate().series);
            A.eq(1, new YCoordinate().collection);
            A.eq(1, new YCoordinate().volume);
            A.eq(1, new YCoordinate().book);
            A.eq(1, new XCoordinate().chapter);
            A.eq(1, new XCoordinate().section);
            A.eq(1, new XCoordinate().scroll);
        });

        TestMain.test("PhextTests", "test_hash_support", () -> {
            Map<Coordinate, String> stuff = Phext.explode("hello world\u0017\u0017\u0017scroll 4\u0001Library 2");
            A.eq("hello world", stuff.get(coord("1.1.1/1.1.1/1.1.1")));
            A.eq("scroll 4", stuff.get(coord("1.1.1/1.1.1/1.1.4")));
            A.eq("Library 2", stuff.get(coord("2.1.1/1.1.1/1.1.1")));
            stuff.put(coord("2.3.4/5.6.7/8.9.1"), "random insertion");
            A.eq("hello world\u0017\u0017\u0017scroll 4\u0001Library 2\u001F\u001F\u001E\u001E\u001E\u001D\u001D\u001D\u001D\u001C\u001C\u001C\u001C\u001C\u001A\u001A\u001A\u001A\u001A\u001A\u0019\u0019\u0019\u0019\u0019\u0019\u0019\u0018\u0018\u0018\u0018\u0018\u0018\u0018\u0018random insertion",
                Phext.implode(stuff));
        });

        TestMain.test("PhextTests", "test_emoji_in_scroll", () -> {
            Coordinate coord = coord("1.1.1/1.1.1/1.1.1");
            String update = Phext.insert("", coord, "Lux \uD83D\uDD06 test");
            A.eq("Lux \uD83D\uDD06 test", Phext.fetch(update, coord));
        });

        TestMain.test("PhextTests", "test_coordinate_above_999_parse_print_round_trip", () -> {
            String address = "1000.1001.1002/1003.1004.1005/1006.1007.1008";
            Coordinate coord = Phext.toCoordinate(address);
            A.eq(1000, coord.z.library);
            A.eq(1001, coord.z.shelf);
            A.eq(1002, coord.z.series);
            A.eq(1003, coord.y.collection);
            A.eq(1004, coord.y.volume);
            A.eq(1005, coord.y.book);
            A.eq(1006, coord.x.chapter);
            A.eq(1007, coord.x.section);
            A.eq(1008, coord.x.scroll);
            A.isTrue(coord.validateCoordinate());
            A.eq(address, coord.toString());
            A.eq("1000.1001.1002;1003.1004.1005;1006.1007.1008", coord.toUrlencoded());
            Coordinate parsed = Coordinate.tryParse(address);
            A.eq(coord, parsed);
            A.eq(address, parsed.toString());
            Coordinate zero = Phext.toCoordinate("0.1.1/1.1.1/1.1.1");
            A.isFalse(zero.validateCoordinate());
            A.eq("", zero.toString());
        });

        TestMain.test("PhextTests", "test_scroll_breaks_advance_past_999", () -> {
            StringBuilder doc = new StringBuilder();
            for (int i = 1; i <= 1001; i++) {
                if (i > 1) doc.append(Phext.SCROLL_BREAK);
                doc.append('s').append(i);
            }
            String d = doc.toString();
            for (long scroll : new long[]{998, 999, 1000, 1001}) {
                A.eq("s" + scroll, Phext.fetch(d, coord("1.1.1/1.1.1/1.1." + scroll)), "fetch at scroll " + scroll);
            }
            List<PositionedScroll> parts = Phext.phokenize(d);
            A.eq(1001, parts.size());
            A.eq(998, parts.get(997).coord.x.scroll);
            A.eq(999, parts.get(998).coord.x.scroll);
            A.eq(1000, parts.get(999).coord.x.scroll);
            A.eq(1001, parts.get(1000).coord.x.scroll);
        });

        TestMain.test("PhextTests", "test_fetch_and_explode_above_999", () -> {
            Coordinate coord = coord("1.1.1/1.1.1/1000.1.1");
            String doc = Phext.replace("", coord, "beyond the cap");
            A.eq("beyond the cap", Phext.fetch(doc, coord));
            A.eq("beyond the cap", Phext.explode(doc).get(coord));
            A.eq("", Phext.fetch(doc, coord("1.1.1/1.1.1/1.1.1")));
        });

        TestMain.test("PhextTests", "test_emoji_boundary_crash", () -> {
            String text = "a".repeat(29) + "\uD83D\uDD06";
            String summary = Phext.createSummary(text);
            A.isTrue(summary.length() > 0);
        });

        TestMain.test("PhextTests", "test_try_from_valid_addresses_round_trip", () -> {
            A.eq("1.1.1/1.1.1/1.1.1", Coordinate.tryParse("1.1.1/1.1.1/1.1.1").toString());
            A.eq("98.76.54/32.10.1/23.45.67", Coordinate.tryParse("98.76.54/32.10.1/23.45.67").toString());
            // Rust uses usize::MAX; the Java component type is long, so the
            // largest component is Long.MAX_VALUE (see class docs).
            Coordinate parsed = Coordinate.tryParse(Long.MAX_VALUE + ".1.1/1.1.1/1.1.1");
            A.eq(Long.MAX_VALUE, parsed.z.library);
        });

        TestMain.test("PhextTests", "test_try_from_malformed_components_return_err", () -> {
            String[] cases = {
                "a.1.1/1.1.1/1.1.1",
                "1..1/1.1.1/1.1.1",
                "1.1.-1/1.1.1/1.1.1",
                "1.1.1/999999999999999999999999999999.1.1/1.1.1",
                "1.1.1/1.x.1/1.1.1",
                "1.1.1/1.1./1.1.1",
                "1.1.1/1.1.1/-2.1.1",
                "1.1.1/1.1.1/1.999999999999999999999999999999.1",
                "1.1.1/1.1.1/1.1.z",
            };
            for (String address : cases) {
                boolean err = false;
                try {
                    Coordinate.tryParse(address);
                } catch (PhextParseException e) {
                    err = true;
                }
                A.isTrue(err, "expected PhextParseException for " + address);
            }
        });

        TestMain.test("PhextTests", "test_try_from_component_spellings", () -> {
            A.eq("1.1.1/1.1.1/1.1.1", Coordinate.tryParse("+1.1.1/1.1.1/1.1.1").toString());
            for (String bad : new String[]{" 1.1.1/1.1.1/1.1.1", "1.1.1/1.1.1/1.1.1 ", "1.1.1/1. 1.1/1.1.1"}) {
                boolean err = false;
                try {
                    Coordinate.tryParse(bad);
                } catch (PhextParseException e) {
                    err = true;
                }
                A.isTrue(err, "expected err for '" + bad + "'");
            }
        });

        TestMain.test("PhextTests", "test_try_from_bad_shapes_return_err", () -> {
            for (String bad : new String[]{"1.1.1/1.1.1", "1.1/1.1.1/1.1.1", "1.1.1.1/1.1.1/1.1.1", ""}) {
                boolean err = false;
                try {
                    Coordinate.tryParse(bad);
                } catch (PhextParseException e) {
                    err = true;
                }
                A.isTrue(err, "expected err for '" + bad + "'");
            }
        });

        TestMain.test("PhextTests", "test_to_coordinate_long_digit_run_saturates", () -> {
            Coordinate coord = Phext.toCoordinate("999999999999999999999999999999999.1.1/1.1.1/1.1.1");
            A.eq(Long.MAX_VALUE, coord.z.library);
        });

        TestMain.test("PhextTests", "test_insert_performance_2k_scrolls", () -> {
            String doc1 = "the quick brown fox jumped over the lazy dog";
            Coordinate next = coord("1.1.1/1.1.1/1.1.1");
            String result = "";
            long start = System.nanoTime();
            for (int x = 1; x <= 2000; x++) {
                if (next.x.scroll > 32) next.sectionBreak();
                if (next.x.section > 32) next.chapterBreak();
                if (next.x.chapter > 32) next.bookBreak();
                result = Phext.insert(result, next, doc1);
                next.scrollBreak();
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            A.isTrue(millis < 5000, "took " + millis + " ms");
            A.eq(coord("1.1.1/1.1.1/2.31.17"), next);
            A.eq(44, doc1.length());
            byte[] check = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int phextTokens = 0, scrollBreaks = 0, sectionBreaks = 0, chapterBreaks = 0;
            for (byte b : check) {
                if (Phext.isPhextBreak(b)) phextTokens++;
                if (b == Phext.SCROLL_BREAK_U8) scrollBreaks++;
                if (b == Phext.SECTION_BREAK_U8) sectionBreaks++;
                if (b == Phext.CHAPTER_BREAK_U8) chapterBreaks++;
            }
            A.eq(1999, phextTokens);
            A.eq(1937, scrollBreaks);
            A.eq(61, sectionBreaks);
            A.eq(1, chapterBreaks);
            A.eq(2000 * 44 + 1999, check.length);
        });

        TestMain.test("PhextTests", "test_insert_performance_medium_scrolls", () -> {
            String doc1 = "the quick brown fox jumped over the lazy dog\n".repeat(1000);
            Coordinate next = coord("1.1.1/1.1.1/1.1.1");
            String result = "";
            long start = System.nanoTime();
            for (int x = 1; x <= 25; x++) {
                if (next.x.scroll > 5) next.sectionBreak();
                if (next.x.section > 5) next.chapterBreak();
                if (next.x.chapter > 5) next.bookBreak();
                result = Phext.insert(result, next, doc1);
                next.scrollBreak();
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            A.isTrue(millis < 1000, "took " + millis + " ms");
            A.eq(coord("1.1.1/1.1.1/1.5.6"), next);
            A.eq(45000, doc1.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            byte[] check = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int phextTokens = 0, lineBreaks = 0, scrollBreaks = 0, sectionBreaks = 0, chapterBreaks = 0;
            for (byte b : check) {
                if (Phext.isPhextBreak(b)) phextTokens++;
                if (b == Phext.LINE_BREAK_U8) lineBreaks++;
                if (b == Phext.SCROLL_BREAK_U8) scrollBreaks++;
                if (b == Phext.SECTION_BREAK_U8) sectionBreaks++;
                if (b == Phext.CHAPTER_BREAK_U8) chapterBreaks++;
            }
            A.eq(25024, phextTokens);
            A.eq(25000, lineBreaks);
            A.eq(20, scrollBreaks);
            A.eq(4, sectionBreaks);
            A.eq(0, chapterBreaks);
            A.eq(25 * (45000 - 1000) + 25024, check.length);
        });

        TestMain.test("PhextTests", "regressions_helios_stalled_parse", () -> {
            // port of libphext-rs src/regressions.rs; the fixture files live
            // at the repo root, as in libphext-rs
            String helios = Files.readString(TestMain.repoRoot.resolve("regression-1-helios.phext"),
                java.nio.charset.StandardCharsets.UTF_8);
            String result = Phext.fetch(helios, coord("1.1.1/1.1.1/1.1.1"));
            long actual = result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            A.isTrue(actual == 358 || actual == 370, "unexpected first-scroll length: " + actual);
            String msg = Files.readString(TestMain.repoRoot.resolve("regression-1-msg.txt"),
                java.nio.charset.StandardCharsets.UTF_8);
            String updated = Phext.replace(helios, coord("2.1.100/1.1.1/1.1.1"), msg);
            long updatedLen = updated.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            A.isTrue(updatedLen == 15150 || updatedLen == 15317, "unexpected replaced length: " + updatedLen);
        });
    }
}
