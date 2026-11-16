package com.htttdn.crm.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "internal_notices")
public class InternalNotice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @Column(name = "notice_type", nullable = false, length = 40)
    private String noticeType;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(length = 200)
    private String link;

    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public User getRecipient() { return recipient; }
    public void setRecipient(User value) { recipient = value; }
    public String getNoticeType() { return noticeType; }
    public void setNoticeType(String value) { noticeType = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    public String getLink() { return link; }
    public void setLink(String value) { link = value; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String value) { dedupeKey = value; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant value) { readAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
