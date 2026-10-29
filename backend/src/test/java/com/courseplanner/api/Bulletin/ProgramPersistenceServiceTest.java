package com.courseplanner.api.Bulletin;

import com.courseplanner.api.Bulletin.Programs.ProgramPersistenceService;
import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.model.entities.enums.ProgramKind;
import com.courseplanner.api.model.entities.program.Program;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.repositories.ProgramCatalogVersionRepository;
import com.courseplanner.api.model.repositories.ProgramRepository;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProgramPersistenceServiceTest {

    @Test
    void testUpsertNewProgramAndCatalogVersion() {

        ProgramRepository programRepo = mock(ProgramRepository.class);
        ProgramCatalogVersionRepository pcvRepo = mock(ProgramCatalogVersionRepository.class);

        ProgramPersistenceService service = new ProgramPersistenceService(programRepo, pcvRepo);

        ProgramMeta meta = ProgramMeta.builder()
                .slug("finance-bsb")
                .programName("Finance")
                .kind(ProgramKind.MAJOR)
                .programUrl("url")
                .catalogLabel("2025-2026")
                .collegeName("School of Business") // optional
                .collegeUrl("college-url")                 // optional
                .catalogRoot("https://catalog.example.edu") // optional
                .degreeLabel("Bachelor of Science in Business") // optional
                .build();

        // no existing program
        when(programRepo.findByCode("finance-bsb")).thenReturn(Optional.empty());

        Program savedProgram = new Program();
        savedProgram.setId(1L);
        savedProgram.setCode("finance-bsb");

        when(programRepo.save(any())).thenReturn(savedProgram);

        // no existing PCV
        when(pcvRepo.findByProgramIdAndCatalogYear(1L, 2025))
                .thenReturn(Optional.empty());

        ProgramCatalogVersion savedPCV = new ProgramCatalogVersion();
        savedPCV.setId(10L);

        when(pcvRepo.save(any())).thenReturn(savedPCV);

        ProgramCatalogVersion result = service.upsertProgram(meta);

        assertNotNull(result);
        assertEquals(10L, result.getId());

        verify(programRepo, times(1)).save(any());  // initial create + dirty update
        verify(pcvRepo, times(1)).save(any());      // one PCV save
    }
}
