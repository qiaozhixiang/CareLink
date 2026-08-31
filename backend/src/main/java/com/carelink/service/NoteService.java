package com.carelink.service;

import com.carelink.entity.CareNote;
import com.carelink.repository.CareNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final CareNoteRepository careNoteRepository;

    /** 获取某老人的所有备注 */
    public List<Map<String, Object>> getNotes(Long elderId) {
        List<CareNote> notes = careNoteRepository.findByElderIdOrderByCreatedAtDesc(elderId);
        return notes.stream().map(this::toMap).collect(Collectors.toList());
    }

    /** 新建备注 */
    @Transactional
    public Map<String, Object> createNote(Long authorId, Long elderId, String content,
                                           String tags, Integer isImportant, String imageUrl) {
        CareNote note = CareNote.builder()
                .elderId(elderId)
                .authorId(authorId)
                .content(content)
                .tags(tags)
                .isImportant(isImportant != null ? isImportant : 0)
                .imageUrl(imageUrl)
                .build();

        note = careNoteRepository.save(note);

        Map<String, Object> result = new HashMap<>();
        result.put("id", note.getId());
        result.put("content", note.getContent());
        return result;
    }

    /** 删除备注 */
    @Transactional
    public void deleteNote(Long id) {
        careNoteRepository.deleteById(id);
    }

    private Map<String, Object> toMap(CareNote n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", n.getId());
        map.put("elderId", n.getElderId());
        map.put("authorId", n.getAuthorId());
        map.put("content", n.getContent());
        map.put("tags", n.getTags());
        map.put("isImportant", n.getIsImportant());
        map.put("imageUrl", n.getImageUrl());
        map.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        return map;
    }
}
