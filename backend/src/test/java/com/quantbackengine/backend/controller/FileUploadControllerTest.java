package com.quantbackengine.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

    @InjectMocks
    private FileUploadController fileUploadController;

    private MockMvc mockMvc;
    private final String TEST_UPLOAD_DIR = "test_uploads";

    @BeforeEach
    void setUp() throws Exception {
        // Set theupload directory to a test folder
        ReflectionTestUtils.setField(fileUploadController, "uploadDir", TEST_UPLOAD_DIR);
        mockMvc = MockMvcBuilders.standaloneSetup(fileUploadController).build();

        // Clean up test directory
        Path path = Paths.get(TEST_UPLOAD_DIR);
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
        Files.createDirectories(path);
    }

    @Test
    void testUploadFile_Success() throws Exception {
        String csvContent = "Date,Open,High,Low,Close,Volume\n2023-01-01,100,110,90,105,1000";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                csvContent.getBytes());

        mockMvc.perform(multipart("/api/v1/data/upload")
                .file(file)
                .param("symbol", "TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.symbol").value("TEST"))
                .andExpect(jsonPath("$.rows").value(1));

        // Verify file exists
        Path filePath = Paths.get(TEST_UPLOAD_DIR, "TEST.csv");
        assertTrue(Files.exists(filePath));
    }

    @Test
    void testUploadFile_InvalidExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "some content".getBytes());

        mockMvc.perform(multipart("/api/v1/data/upload")
                .file(file)
                .param("symbol", "TEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Only CSV files are allowed")));
    }

    @Test
    void testUploadFile_EmptyContent() throws Exception {
        // File with only header considered "empty" for data purposes by our logic?
        // No, rowCount <= 0 means header only or empty.
        String csvContent = "Date,Open,High,Low,Close,Volume";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.csv",
                "text/csv",
                csvContent.getBytes());

        mockMvc.perform(multipart("/api/v1/data/upload")
                .file(file)
                .param("symbol", "TEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("CSV file is empty or contains only header")));

        // ensure file was deleted
        Path filePath = Paths.get(TEST_UPLOAD_DIR, "TEST.csv");
        assertFalse(Files.exists(filePath));
    }
}
