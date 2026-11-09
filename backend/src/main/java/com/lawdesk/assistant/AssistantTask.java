package com.lawdesk.assistant;

/** What the member asks the assistant to do (requirements section 22.1 to 22.6). Every task only reads. */
public enum AssistantTask {
    /** 22.6: a question about one case, or about the judgments library when no case is given. */
    QUESTION,
    /** 22.1: the facts, chronology, requests, defences, judgments, last and next step of a case. */
    CASE_SUMMARY,
    /** 22.2: dates, case numbers, requests, defences and operative parts found in one document. */
    DOCUMENT_ANALYSIS,
    /** 22.3: judgments of the library that resemble a case, or contain the words of a question. */
    PRECEDENTS,
    /** 22.4: a first draft of a memorandum, assembled from what the case records; stored as a non-final draft. */
    MEMO_DRAFT,
    /** 22.5: two documents compared sentence by sentence. */
    COMPARISON
}
