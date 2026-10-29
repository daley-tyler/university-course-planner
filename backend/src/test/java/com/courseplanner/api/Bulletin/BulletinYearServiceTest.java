package com.courseplanner.api.Bulletin;

import com.courseplanner.api.Bulletin.Programs.BulletinYearService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class BulletinYearServiceTest {

    private BulletinYearService service;

    @BeforeEach
    void setup() {
        service = new BulletinYearService();
    }

    @Test
    void testGetAllBulletinRoots_futureProof() throws Exception {

        String fakeArchiveHtml = """
        <html>
          <body>
            <a href="/archive/2024-2025/">2024-2025</a>
            <a href="/archive/2023-2024/">2023-2024</a>
            <a href="/archive/2022-2023/">2022-2023</a>
          </body>
        </html>
        """;

        Document mockDoc = Jsoup.parse(fakeArchiveHtml);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {

            org.jsoup.Connection mockConn = mock(org.jsoup.Connection.class);

            jsoupMock.when(() -> Jsoup.connect("https://catalog.example.edu/archive/"))
                    .thenReturn(mockConn);

            // Chain mocking
            when(mockConn.userAgent(anyString())).thenReturn(mockConn);
            when(mockConn.timeout(anyInt())).thenReturn(mockConn);
            when(mockConn.get()).thenReturn(mockDoc);

            List<String> roots = service.getAllBulletinRoots();

            assertEquals(4, roots.size());
            assertTrue(roots.contains("https://catalog.example.edu"));
            assertTrue(roots.contains("https://catalog.example.edu/archive/2024-2025/"));
            assertTrue(roots.contains("https://catalog.example.edu/archive/2023-2024/"));
            assertTrue(roots.contains("https://catalog.example.edu/archive/2022-2023/"));
        }
    }

    @Test
    void testGetLastNBulletins_futureProof() throws Exception {

        String fakeArchiveHtml = """
        <html>
          <body>
            <a href="/archive/2099-2100/">2099-2100</a>
            <a href="/archive/2098-2099/">2098-2099</a>
            <a href="/archive/2097-2098/">2097-2098</a>
            <a href="/archive/2096-2097/">2096-2097</a>
            <a href="/archive/2095-2096/">2095-2096</a>
            <a href="/archive/2094-2095/">2094-2095</a>
          </body>
        </html>
        """;

        Document mockDoc = Jsoup.parse(fakeArchiveHtml);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {

            org.jsoup.Connection mockConn = mock(org.jsoup.Connection.class);

            jsoupMock.when(() -> Jsoup.connect("https://catalog.example.edu/archive/"))
                    .thenReturn(mockConn);

            // Must chain mock SAME as above
            when(mockConn.userAgent(anyString())).thenReturn(mockConn);
            when(mockConn.timeout(anyInt())).thenReturn(mockConn);
            when(mockConn.get()).thenReturn(mockDoc);

            List<String> last5 = service.getLastNBulletins(5);

            assertEquals(5, last5.size());

            // #1 is always the CURRENT bulletin (stable)
            assertEquals("https://catalog.example.edu", last5.get(0));

            // Sorted years in descending order
            assertTrue(last5.get(1).contains("2099-2100"));
            assertTrue(last5.get(2).contains("2098-2099"));
            assertTrue(last5.get(3).contains("2097-2098"));
            assertTrue(last5.get(4).contains("2096-2097"));
        }
    }
}
