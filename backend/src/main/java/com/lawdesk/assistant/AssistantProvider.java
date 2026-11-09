package com.lawdesk.assistant;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The provider PORT of the legal assistant (requirements section 22; owner decision D8). Whatever composes the wording of an
 * answer sits behind this interface and receives only what the retrieval already found, as DATA to be quoted: the passages are
 * never instructions, and a provider cannot read, search or change anything itself.
 *
 * <p><b>No provider is approved.</b> The only implementation is {@link LocalTestProvider}, deterministic and local, marked
 * non-production. An adapter for an external service would have to be approved by the owner first (see
 * docs/design/LD-DOC-09-assistant-provider-options.md); until then no class of this package may open a connection, and
 * {@code NoNetworkTest} fails the build if one does.
 */
public interface AssistantProvider {

    /** Who composes the answers: a stable code, whether it is a test stand-in and whether it sends anything out of the system. */
    record Info(String code, boolean nonProduction, boolean external) {
    }

    /** One stored value of a structured source, named by a code the screens translate. */
    record Field(String code, String value) {
    }

    /**
     * A passage the answer may rest on. {@code excerpt} is cut from the original text of a record the user may see;
     * {@code fields} is used instead for a structured record. {@code page} is null: stored text keeps no page boundaries.
     * {@code suspectedInstruction} marks text that reads like an instruction to an assistant; it is still only data.
     */
    record Source(int n, String type, UUID id, UUID caseId, String caseReferenceNo, String title, LocalDate date, Integer page,
            String excerpt, List<Field> fields, boolean suspectedInstruction) {
    }

    /** A named group of sources the answer is made of, in the order the answer shows them. */
    record Section(String code, List<Integer> sources) {
    }

    record Request(AssistantTask task, String question, List<Section> sections, List<Source> sources) {
    }

    /**
     * A piece of the answer. {@code kind}: {@code HEADING} (a fixed section title, by code), {@code QUOTE} (stored text or
     * fields quoted from the sources numbered in {@code sources} - a stored fact), {@code NOTE} (a fixed message by code,
     * written by the assistant and not a source - a remark, never a fact).
     */
    record Block(String kind, String code, String text, List<Field> fields, List<Integer> sources) {
    }

    record Composition(List<Block> blocks) {
    }

    Info info();

    Composition compose(Request request);
}
