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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

    @InjectMocks
    private FileUploadController fileUploadController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(fileUploadController).build();
        // Create a temporary directory for uploads
        Path tempDir = Files.createTempDirectory("uploads");
        ReflectionTestUtils.setField(fileUploadController, "uploadDir", tempDir.toString());
    }

    @Test
    void uploadFile_ShouldReturnSuccess_WhenFileIsValid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                MediaType.TEXT_PLAIN_VALUE,
                "date,close\n2023-01-01,100".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/data/upload")
                        .file(file)
                        .param("symbol", "TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.symbol").value("TEST"));
    }

    @Test
    void uploadFile_ShouldReturnBadRequest_WhenFileIsNotCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/data/upload")
                        .file(file)
                        .param("symbol", "TEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only CSV files are allowed"));
    }

    @Test
    void uploadFile_ShouldReturnBadRequest_WhenContentTypeIsInvalid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.csv",
                MediaType.IMAGE_PNG_VALUE,
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/data/upload")
                        .file(file)
                        .param("symbol", "TEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid content type. Only CSV files are allowed"));
    }
}
