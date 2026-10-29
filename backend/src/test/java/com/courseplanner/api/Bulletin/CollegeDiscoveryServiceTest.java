package com.courseplanner.api.Bulletin;

import com.courseplanner.api.Bulletin.Programs.CollegeDiscoveryService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CollegeDiscoveryServiceTest {
    @Test void selectsSupportedSchoolPathsAndIgnoresUnrelatedLinks() throws Exception {
        var doc=Jsoup.parse("""
            <div id="cl-menu"><ul>
              <li class="isparent"><a href="/business/">Invented Rover School</a></li>
              <li class="isparent"><a href="/not-a-school/">Unrelated link</a></li>
              <li class="isparent"><a href="/arts-science">Invented Mural School</a></li>
            </ul></div>
            """);
        var connection=mock(org.jsoup.Connection.class);
        try(var network=mockStatic(Jsoup.class)) {
            network.when(()->Jsoup.connect("https://catalog.example.edu")).thenReturn(connection);
            when(connection.userAgent(anyString())).thenReturn(connection);
            when(connection.timeout(anyInt())).thenReturn(connection);
            when(connection.get()).thenReturn(doc);
            var links=new CollegeDiscoveryService().getColleges("https://catalog.example.edu");
            assertThat(links).extracting(link->link.name()).containsExactly("Invented Rover School","Invented Mural School");
            assertThat(links).extracting(link->link.url()).containsExactly("https://catalog.example.edu/business/","https://catalog.example.edu/arts-science/");
        }
    }
}
