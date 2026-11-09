package com.lawdesk.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * The deterministic local TEST provider (NON_PRODUCTION). It composes an answer extractively: for each section it writes a
 * fixed heading and then quotes, word for word, the passages the retrieval handed it, each tagged with its source number.
 * It generates no sentence, interprets no passage, opens no connection and keeps no state, so the same request always gives the
 * same answer. It exists to prove the components (retrieval, citation, refusal, access, logging); it is NOT evidence that an
 * operational assistant works.
 */
@Component
class LocalTestProvider implements AssistantProvider {

    static final String CODE = "LOCAL_EXTRACTIVE_TEST";

    @Override
    public Info info() {
        return new Info(CODE, true, false);
    }

    @Override
    public Composition compose(Request request) {
        Map<Integer, Source> byNumber = request.sources().stream().collect(Collectors.toMap(Source::n, Function.identity()));
        List<Block> blocks = new ArrayList<>();
        for (Section section : request.sections()) {
            blocks.add(new Block("HEADING", section.code(), null, null, List.of()));
            if (section.sources().isEmpty()) {
                blocks.add(new Block("NOTE", "SECTION_EMPTY", null, null, List.of()));
                continue;
            }
            for (int n : section.sources()) {
                Source source = byNumber.get(n);
                if (source != null) {
                    blocks.add(new Block("QUOTE", null, source.excerpt(), source.excerpt() == null ? source.fields() : null, List.of(n)));
                }
            }
        }
        blocks.add(new Block("NOTE", "DRAFT_FOR_REVIEW", null, null, List.of()));
        return new Composition(List.copyOf(blocks));
    }
}
