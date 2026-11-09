package com.lawdesk.assistant;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Recognises text that reads like an instruction to an assistant ("ignore the previous instructions", "delete the case").
 * Text found in documents, notes and records is DATA to be quoted: it never changes what the assistant does, and nothing the
 * assistant does depends on this check. The mark only lets a screen warn the reader that a quoted passage is phrased as a
 * command. It is a deliberately broad list of phrases (Arabic and English), not a defence the design relies on.
 */
final class InstructionMarkers {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+|your\\s+)?(previous|prior|above|earlier|system)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(system|developer)\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(delete|drop|remove|erase|close|approve|send|email|transfer)\\b.{0,40}\\b(case|record|file|document|memo|user|account)s?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(reveal|show|list|print|dump)\\b.{0,40}\\b(other|all|every|hidden|confidential)\\b.{0,30}\\b(cases?|documents?|records?|passwords?|secrets?)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("تجاهل|تجاهلي|انس[َ]?\\s+(كل|جميع)|تعليمات\\s+(سابقة|النظام)"),
            Pattern.compile("(احذف|امسح|أغلق|اغلق|اعتمد|أرسل|ارسل)\\s+.{0,30}(القضية|القضايا|المستند|المذكرة|السجل|المستخدم|الحساب)"),
            Pattern.compile("(أظهر|اظهر|اكشف|اعرض)\\s+.{0,30}(القضايا|المستندات|السجلات|كلمات\\s+المرور)"),
            Pattern.compile("أنت\\s+الآن|انت\\s+الان"));

    private InstructionMarkers() {
    }

    static boolean looksLikeInstruction(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }
}
