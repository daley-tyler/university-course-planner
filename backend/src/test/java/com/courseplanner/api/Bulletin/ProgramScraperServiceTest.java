package com.courseplanner.api.Bulletin;

import com.courseplanner.api.Bulletin.Programs.*;
import com.courseplanner.api.Bulletin.dto.CollegeLink;
import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.model.entities.enums.ProgramKind;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProgramScraperServiceTest {
    @Test void sendsEveryParsedProgramToPersistence() throws Exception {
        String root="https://catalog.example.edu/archive/2091-2092/";
        var discovery=mock(CollegeDiscoveryService.class);
        var parser=mock(CollegePageParser.class);
        var persistence=mock(ProgramPersistenceService.class);
        var years=mock(BulletinYearService.class);
        var college=new CollegeLink("Invented Rover School","/business/",root+"business/");
        var doc=Jsoup.parse("<h1>Invented Rover School</h1>",college.url());
        var meta=ProgramMeta.builder().catalogRoot(root).catalogLabel("2091-2092")
                .collegeName(college.name()).collegeUrl(college.url()).programName("Rover Workshop")
                .programUrl(root+"rover-workshop/").slug("rover-workshop").kind(ProgramKind.MAJOR)
                .degreeLabel("Bachelor of Rover Studies").build();
        var version=new ProgramCatalogVersion();
        version.setId(73L);
        when(discovery.getColleges(root)).thenReturn(List.of(college));
        when(parser.parsePrograms(root,college,doc)).thenReturn(List.of(meta));
        when(persistence.upsertProgram(meta)).thenReturn(version);
        var connection=mock(org.jsoup.Connection.class);
        try(var network=mockStatic(Jsoup.class)) {
            network.when(()->Jsoup.connect(college.url())).thenReturn(connection);
            when(connection.userAgent(anyString())).thenReturn(connection);
            when(connection.timeout(anyInt())).thenReturn(connection);
            when(connection.get()).thenReturn(doc);
            var result=new ProgramScraperService(years,discovery,parser,persistence).scrapeAndStoreForRoot(root);
            assertThat(result).containsExactly(version);
            verify(discovery).getColleges(root);
            verify(parser).parsePrograms(root,college,doc);
            verify(persistence).upsertProgram(meta);
            verifyNoMoreInteractions(persistence);
        }
    }
}
