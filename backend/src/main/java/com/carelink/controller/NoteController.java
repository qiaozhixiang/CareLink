package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.dto.NoteRequest;
import com.carelink.entity.User;
import com.carelink.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getNotes(@RequestParam Long elderId) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.getNotes(elderId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createNote(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NoteRequest request) {
        Map<String, Object> result = noteService.createNote(
                user.getId(),
                request.getElderId(),
                request.getContent(),
                request.getTags(),
                request.getIsImportant(),
                request.getImageUrl()
        );
        return ResponseEntity.ok(ApiResponse.ok("备注已保存", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id);
        return ResponseEntity.ok(ApiResponse.ok("备注已删除", null));
    }
}
