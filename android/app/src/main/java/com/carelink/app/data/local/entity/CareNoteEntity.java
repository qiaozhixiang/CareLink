package com.carelink.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 家属共享备注 */
@Entity(tableName = "care_notes")
public class CareNoteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long elderId;
    public long authorId;
    public String content;
    public String tags;
    public int isImportant;   // 1=重要
    public long createdAt;
    public String imageUrl;   // 可选图片
}
