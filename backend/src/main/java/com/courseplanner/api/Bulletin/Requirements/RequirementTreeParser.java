package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import com.courseplanner.api.Bulletin.dto.ParsedReqNodeCourseOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementTreeParser {

    private final CourseLookupService courseLookupService;

    private static final Pattern HOURS_RANGE_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern HOURS_SINGLE_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern N_OF_COURSES_PATTERN =
            Pattern.compile("\\((\\d+)\\s+courses?\\)", Pattern.CASE_INSENSITIVE);
    private static final String COUNT_TOKEN_PATTERN =
            "(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)";
    private static final String NUMBER_TOKEN_PATTERN =
            "(?:\\d+(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)";
    private static final String HOURS_LABEL_PATTERN =
            "(?:credit\\s+hours?|hours?|credits?|semester\\s+hours?)";
    private static final String DOUBLE_COUNT_CONTEXT_PATTERN =
            "(?:double[- ]count(?:ed|ing)?|count\\s+toward\\s+both|count\\s+towards\\s+both|count\\s+for\\s+both|used\\s+for\\s+both|used\\s+toward\\s+both|used\\s+towards\\s+both|used\\s+in\\s+both|applied\\s+to\\s+both|may\\s+also\\s+count|may\\s+also\\s+apply)";
    private static final Pattern LEVEL_HEADER_PATTERN =
            Pattern.compile("(\\d{3})\\s*[- ]?level", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACK_HEADER_PATTERN =
            Pattern.compile("(?i)^\\s*tracks?\\b");
    private static final Pattern TRACK_OPTION_PATTERN =
            Pattern.compile("(?i)^(?:track\\s+)?[A-Z]\\s*[:.)-]\\s*.*\\btrack\\b");
    private static final Pattern TRACK_OPTION_INDEX_PATTERN =
            Pattern.compile("(?i)^(?:track\\s+)?(?:[A-Z]|\\d+)\\s*[-:.]\\s+.+");
    private static final Pattern SELECT_COUNT_PATTERN =
            Pattern.compile("(?i)(select|choose|take|complete)\\s+(?:(?:these|those)\\s+)?((?:at least|a\\s+minimum\\s+of|minimum\\s+of)\\s+)?(" + COUNT_TOKEN_PATTERN + ")\\s*(courses?)?(?:\\s+of\\s+the\\s+following)?");
    private static final Pattern SELECT_ONE_PATTERN =
            Pattern.compile("(?i)(select|choose|take|complete)\\s+one\\b");
    private static final Pattern SELECT_ALL_PATTERN =
            Pattern.compile("(?i)(select|choose|take|complete)\\s+all\\s+of\\s+the\\s+following");
    private static final Pattern SELECT_THE_FOLLOWING_PATTERN =
            Pattern.compile("(?i)(select|choose|take|complete)\\s+the\\s+following");
    private static final Pattern SELECT_HOURS_RANGE_PATTERN =
            Pattern.compile("(?i)(select|choose|take|complete)\\s+(?:at least\\s+|a\\s+minimum\\s+of\\s+|minimum\\s+of\\s+)?(" + NUMBER_TOKEN_PATTERN + ")\\s*(?:to|-)\\s*(" + NUMBER_TOKEN_PATTERN + ")\\s+" + HOURS_LABEL_PATTERN);
    private static final Pattern SELECT_HOURS_SINGLE_PATTERN =
            Pattern.compile("(?i)(select|choose|take|complete)\\s+(?:at least\\s+|a\\s+minimum\\s+of\\s+|minimum\\s+of\\s+)?(" + NUMBER_TOKEN_PATTERN + ")(?:\\s+\\w+){0,2}\\s+" + HOURS_LABEL_PATTERN);
    private static final Pattern SELECT_HOURS_UP_TO_PATTERN =
            Pattern.compile("(?i)(select|choose|take|complete)\\s+up\\s+to\\s+(" + NUMBER_TOKEN_PATTERN + ")(?:\\s+\\w+){0,2}\\s+" + HOURS_LABEL_PATTERN);
    private static final Pattern SELECT_HOURS_TO_EQUAL_PATTERN =
            Pattern.compile("(?i)to\\s+equal\\s+(" + NUMBER_TOKEN_PATTERN + ")(?:\\s+\\w+){0,2}\\s+" + HOURS_LABEL_PATTERN);
    private static final Pattern TAKE_BOTH_PATTERN =
            Pattern.compile("(?i)take\\s+both");
    private static final Pattern DOUBLE_COUNT_CAP_PATTERN =
            Pattern.compile("(?i)(?:only|up\\s+to|no\\s+more\\s+than)\\s+(" + NUMBER_TOKEN_PATTERN + ")\\s+" + HOURS_LABEL_PATTERN + ".*?" + DOUBLE_COUNT_CONTEXT_PATTERN);
    private static final Pattern COURSE_GROUP_CAP_PATTERN =
            Pattern.compile("(?i)(?:no\\s+more\\s+than|not\\s+more\\s+than|at\\s+most|maximum\\s+of)\\s+(" + COUNT_TOKEN_PATTERN + ")\\s+courses?");
    private static final Pattern DOUBLE_COUNT_DENY_PATTERN =
            Pattern.compile("(?i)(?:cannot|can\\s+not|may\\s+not|must\\s+not|not)\\s+(?:be\\s+)?double[- ]count(?:ed|ing)?");
    private static final Pattern DOUBLE_COUNT_BOTH_DENY_PATTERN =
            Pattern.compile("(?i)(?:cannot|can\\s+not|may\\s+not|must\\s+not)\\s+.*?(?:count|apply|be\\s+used)\\s+(?:toward|towards|for)\\s+both");
    private static final Pattern LEVEL_CAP_HOURS_PATTERN =
            Pattern.compile("(?i)no more than\\s+(\\d+(?:\\.\\d+)?)\\s+hours?.*?(\\d{3})\\s*[- ]?level");
    private static final Pattern LEVEL_CAP_COURSES_PATTERN =
            Pattern.compile("(?i)no more than\\s+(" + COUNT_TOKEN_PATTERN + ")\\s+(?:courses?\\s+)?(?:at\\s+)?(\\d{3})\\s*[- ]?level");

    /**
     * Entry point: parse the entire program requirement structure from a program page.
     * Always returns a non-null root ("Program Requirements") so persistence is simpler.
     */
    public ParsedReqNode parseRequirements(Document doc) {
        Element textContainer = doc.selectFirst("#textcontainer");
        if (textContainer == null) {
            log.warn("No #textcontainer found on page {}", safeLocation(doc));
            return ParsedReqNode.builder()
                    .name("Program Requirements")
                    .kind("ALL_OF")
                    .sourceUrl(safeLocation(doc))
                    .children(new ArrayList<>())
                    .build();
        }

        ParsedReqNode root = ParsedReqNode.builder()
                .name("Program Requirements")
                .kind("ALL_OF")
                .sourceUrl(safeLocation(doc))
                .children(new ArrayList<>())
                .build();

        List<Footnote> footnoteMap = buildFootnoteMap(textContainer);
        Set<Element> usedFootnotes = new LinkedHashSet<>();
        for (Element table : textContainer.select("table.sc_courselist")) {
            String tableLabel = findTableLabel(table);
            ParsedReqNode tableNode = parseRequirementTable(table, tableLabel, footnoteMap, usedFootnotes);
            if (tableNode != null) root.getChildren().add(tableNode);
        }

        // Narrative requirements outside tables (paragraphs/lists)
        for (ParsedReqNode note : extractNarrativeRequirements(textContainer, footnoteMap, usedFootnotes)) {
            root.getChildren().add(note);
        }

        // A selector can be pruned after consuming a footnote. Check the retained
        // tree too so a policy is never silently lost with that selector.
        Set<String> retainedFootnotes = new LinkedHashSet<>();
        collectRetainedFootnotes(root, retainedFootnotes);
        // Footnotes under the text container
        for (Element dt : textContainer.select("dl.sc_footnotes > dt")) {
            Element dd = dt.nextElementSibling();
            if (dd == null || !"dd".equalsIgnoreCase(dd.tagName())) continue;
            Integer num = parseFootnoteNumber(dt.text());
            if (num == null) continue;
            String ft = normalize(dd.text());
            if (ft.isEmpty() || (usedFootnotes.contains(dd) && retainedFootnotes.contains(ft))) continue;
            ParsedReqNode.ParsedReqNodeBuilder noteBuilder = ParsedReqNode.builder()
                    .name("Footnote")
                    .kind("NOTE_ONLY")
                    .notes(ft)
                    .footnoteText(ft)
                    .sourceUrl(safeLocation(doc))
                    .sourceRef(dd.cssSelector());
            applyDoubleCountFromText(ft, noteBuilder);
            ParsedReqNode note = noteBuilder.build();
            root.getChildren().add(note);
        }

        // Deep prune to remove null/empty scaffolding
        pruneTree(root);
        return root;
    }

    /** Parse a single <table class="sc_courselist"> into a subtree (or null if nothing valid). */
    private ParsedReqNode parseRequirementTable(
            Element table,
            String tableLabel,
            List<Footnote> footnoteMap,
            Set<Element> usedFootnotes
    ) {
        String label = (tableLabel == null || tableLabel.isBlank()) ? "Table Requirements" : tableLabel;
        ParsedReqNode tableRoot = ParsedReqNode.builder()
                .name(label)
                .kind("ALL_OF")
                .sourceRef(table.cssSelector())
                .children(new ArrayList<>())
                .build();

        Elements rows = table.select("> tbody > tr");
        ParsedReqNode currentSection = null;
        ParsedReqNode sectionRoot = null;
        ParsedReqNode lastAdded = null;
        ExpressionState expr = new ExpressionState();
        boolean lastRowWasConnector = false;
        ParsedReqNode activeTrackGroup = null;
        ParsedReqNode activeTrackOption = null;

        for (int i = 0; i < rows.size(); i++) {
            Element tr = rows.get(i);
            if (tr.className().contains("orclass")) {
                continue; // consumed by previous non-or row
            }
            String rowClass = tr.className();
            boolean isCourse = isCourseRow(tr) && !tr.className().contains("orclass");
            boolean isConnector = isConnectorRow(tr);

            // If a connector sequence just ended, materialize the expression before proceeding.
            if (expr.isActive() && expr.pendingConnector == null && !isConnector) {
                if (!isCourse || !lastRowWasConnector) {
                    lastAdded = flushExpression(expr, currentSection, lastAdded);
                }
            }

            // Headers create new sections (e.g., "Commerce Core", "Select two of the following:")
            if (rowClass.contains("areaheader")) {
                lastAdded = flushExpression(expr, currentSection, lastAdded);
                ParsedReqNode headerNode = buildSectionFromHeader(tr, footnoteMap, usedFootnotes);
                if (headerNode == null) {
                    lastRowWasConnector = false;
                    continue;
                }

                String headerName = headerNode.getName() != null ? headerNode.getName() : "";
                boolean isTrackHeader = TRACK_HEADER_PATTERN.matcher(headerName).find();
                if (isTrackHeader) {
                    currentSection = headerNode;
                    tableRoot.getChildren().add(headerNode);
                    sectionRoot = headerNode;
                    activeTrackGroup = headerNode;
                    activeTrackOption = null;
                    lastAdded = null;
                    expr.reset();
                    lastRowWasConnector = false;
                    continue;
                }

                if (activeTrackGroup != null) {
                    if (isTrackOptionHeader(headerName, true)) {
                        ParsedReqNode trackOption = buildTrackOptionNode(headerName, tr, footnoteMap, usedFootnotes);
                        ensureChildren(activeTrackGroup).add(trackOption);
                        currentSection = trackOption;
                        sectionRoot = trackOption;
                        activeTrackOption = trackOption;
                        lastAdded = null;
                        expr.reset();
                        lastRowWasConnector = false;
                        continue;
                    }

                    if (activeTrackOption != null) {
                        ensureChildren(activeTrackOption).add(headerNode);
                        currentSection = headerNode;
                        sectionRoot = headerNode;
                        lastAdded = null;
                        expr.reset();
                        lastRowWasConnector = false;
                        continue;
                    }

                    activeTrackGroup = null;
                    activeTrackOption = null;
                }

                currentSection = headerNode;
                tableRoot.getChildren().add(currentSection);
                sectionRoot = currentSection;
                lastAdded = null;
                expr.reset();
                lastRowWasConnector = false;
                continue;
            }

            // Comment rows that describe selection rules but are not areaheaders
            if (containsComment(tr)) {
                String commentText = extractCommentText(tr);
                String connector = parseConnector(commentText);
                if (connector != null && currentSection != null) {
                    if (!expr.isActive()) {
                        if (lastAdded != null) {
                            currentSection.getChildren().remove(lastAdded);
                            expr.start(lastAdded);
                            lastAdded = null;
                        } else {
                            connector = null;
                        }
                    }
                    if (expr.isActive() && connector != null) {
                        expr.pendingConnector = connector;
                        lastRowWasConnector = true;
                        continue;
                    }
                }

                ParsedReqNode commentNode = buildNodeFromComment(tr, footnoteMap, usedFootnotes);
                if (commentNode != null) {
                    boolean isSelector = isSelectorComment(commentText);
                    boolean isTrackOption = "TRACK_OPTION".equals(commentNode.getKind())
                            || (activeTrackGroup != null && isTrackOptionHeader(commentNode.getName(), true));
                    if (isTrackOption) {
                        ParsedReqNode parent = activeTrackGroup != null ? activeTrackGroup
                                : (sectionRoot != null ? sectionRoot : tableRoot);
                        ensureChildren(parent).add(commentNode);
                        currentSection = commentNode;
                        sectionRoot = commentNode;
                        activeTrackOption = commentNode;
                        lastAdded = null;
                        expr.reset();
                    } else if (isSelector) {
                        ParsedReqNode parent = activeTrackOption != null ? activeTrackOption
                                : (sectionRoot != null ? sectionRoot : tableRoot);
                        ensureChildren(parent).add(commentNode);
                        currentSection = commentNode;
                        lastAdded = null;
                        expr.reset();
                    } else if (currentSection != null) {
                        ensureChildren(currentSection).add(commentNode);
                    } else {
                        ensureChildren(tableRoot).add(commentNode);
                    }
                }
                lastRowWasConnector = false;
                continue;
            }

            // If we see course rows without an explicit header, start a default section
            if (currentSection == null && isCourse) {
                currentSection = ParsedReqNode.builder()
                        .name("Required Courses")
                        .kind("ALL_OF")
                        .sourceRef(tr.cssSelector())
                        .children(new ArrayList<>())
                        .build();
                tableRoot.getChildren().add(currentSection);
                sectionRoot = currentSection;
            }

            // Course/OR groups
            if (currentSection != null && isCourse) {
                ParsedReqNode courseNode = buildCourseNodeFromRow(rows, i, footnoteMap, usedFootnotes);
                if (courseNode != null) {
                    if (expr.isActive() && expr.pendingConnector != null) {
                        expr.ops.add(expr.pendingConnector);
                        expr.terms.add(courseNode);
                        expr.pendingConnector = null;
                    } else if (expr.isActive()) {
                        lastAdded = flushExpression(expr, currentSection, lastAdded);
                        currentSection.getChildren().add(courseNode);
                        lastAdded = courseNode;
                    } else {
                        currentSection.getChildren().add(courseNode);
                        lastAdded = courseNode;
                    }
                }
                lastRowWasConnector = false;
            }

            // Totals/summary rows
            if (isTotalHoursRow(tr)) {
                ParsedReqNode totalNode = parseTotalHoursRow(tr);
                if (totalNode != null) tableRoot.getChildren().add(totalNode);
                lastRowWasConnector = false;
            }
        }

        // Flush any trailing connector expression.
        flushExpression(expr, currentSection, lastAdded);

        // Prune this subtree (also prunes sections within)
        pruneTree(tableRoot);

        // If it completely evaporated, drop this table
        if (isEffectivelyEmpty(tableRoot)) return null;

        // If it contains exactly one real child, collapse only when the table label is generic.
        if (tableRoot.getChildren() != null && tableRoot.getChildren().size() == 1) {
            ParsedReqNode only = tableRoot.getChildren().getFirst();
            String tableName = normalize(tableRoot.getName());
            String childName = normalize(only.getName());
            boolean tableNameGeneric = tableName.isEmpty()
                    || "table requirements".equalsIgnoreCase(tableName)
                    || "program requirements".equalsIgnoreCase(tableName);
            if (!tableNameGeneric && "required courses".equalsIgnoreCase(childName)) {
                return tableRoot;
            }
            if (!tableNameGeneric && !tableName.equalsIgnoreCase(childName)
                    && !"unnamed requirement block".equalsIgnoreCase(childName)) {
                return tableRoot;
            }
            if (!"Unnamed Requirement Block".equals(only.getName())) return only;
        }

        return tableRoot;
    }

    /* ==============================
       Helpers for Parsing Rows
       ============================== */

    private ParsedReqNode buildSectionFromHeader(
            Element tr,
            List<Footnote> footnoteMap,
            Set<Element> usedFootnotes
    ) {
        Element labelEl = tr.selectFirst("span.courselistcomment");
        if (labelEl == null) labelEl = tr.selectFirst("td");
        if (labelEl == null) return null;

        String raw = normalize(labelEl.text());
        if (raw.isEmpty()) return null;

        String display = cleanHeaderLabel(raw);
        ParsedReqNode.ParsedReqNodeBuilder b = ParsedReqNode.builder()
                .name(display)
                .kind("ALL_OF")
                .sourceRef(tr.cssSelector())
                .children(new ArrayList<>());

        boolean isExplicitNof = false;
        String kind = "ALL_OF";
        // "(4 courses)"
        Matcher mN = N_OF_COURSES_PATTERN.matcher(raw);
        if (mN.find()) {
            kind = "N_OF";
            b.nRequired(Integer.parseInt(mN.group(1)));
            isExplicitNof = true;
        }

        // Header rows use the same explicit course-count instructions as comment rows.
        Matcher wordCount = SELECT_COUNT_PATTERN.matcher(raw);
        if (!isExplicitNof && wordCount.find() && !raw.matches("(?is).*\\bsemesters?\\b.*")) {
            Integer count = parseCountToken(wordCount.group(3));
            boolean courseCount = wordCount.group(4) != null
                    || (!raw.toLowerCase(Locale.ROOT).contains("hour") && !raw.toLowerCase(Locale.ROOT).contains("credit"));
            if (count != null && courseCount) {
                kind = count == 1 ? "ANY_OF" : "N_OF";
                b.nRequired(count);
                isExplicitNof = true;
            }
        }

        // "18-30" (range) or just a single visible number
        Matcher mRange = HOURS_RANGE_PATTERN.matcher(raw);
        Matcher mSingle = HOURS_SINGLE_PATTERN.matcher(raw);
        boolean hasHoursContext = raw.toLowerCase(Locale.ROOT).contains("hour")
                || raw.toLowerCase(Locale.ROOT).contains("credit");
        if (!isExplicitNof && hasHoursContext) {
            if (mRange.find()) {
                kind = "CREDIT_AT_LEAST";
                b.creditMin(new BigDecimal(mRange.group(1)))
                        .creditMax(new BigDecimal(mRange.group(2)));
            } else if (mSingle.find()) {
                // Be conservative: if header includes an isolated number, treat as minimum constraint
                try {
                    BigDecimal v = new BigDecimal(mSingle.group(1));
                    if (v.signum() > 0) {
                        kind = "CREDIT_AT_LEAST";
                        b.creditMin(v);
                    }
                } catch (NumberFormatException ignore) { /* safe */ }
            }
        }

        // "300 Level Requirement"
        Matcher levelMatch = LEVEL_HEADER_PATTERN.matcher(raw);
        if (levelMatch.find()) {
            try {
                int lvl = Integer.parseInt(levelMatch.group(1));
                if ("ALL_OF".equals(kind) && !raw.toLowerCase(Locale.ROOT).contains("no more than")) {
                    kind = "LEVEL_AT_LEAST";
                }
                b.levelMin(lvl);
            } catch (NumberFormatException ignore) { /* safe */ }
        }

        if (TRACK_HEADER_PATTERN.matcher(display).find() && "ALL_OF".equals(kind)) {
            kind = "ANY_OF";
            if (!isExplicitNof) {
                b.nRequired(1);
            }
        }

        // Supplement credit info from the hours column when not already captured from label text.
        // Real headers often carry "18-30" in <td class="hourscol"> with nothing numeric in the label
        // (e.g., "Minor, second major, or co-major outside the CSE department" with hourscol "18-30").
        Element hoursCell = tr.selectFirst("td.hourscol");
        String hoursCellText = normalize(hoursCell != null ? hoursCell.text() : "");
        if (!hoursCellText.isEmpty()) {
            Matcher mHR = HOURS_RANGE_PATTERN.matcher(hoursCellText);
            Matcher mHS = HOURS_SINGLE_PATTERN.matcher(hoursCellText);
            if ("ALL_OF".equals(kind)) {
                // No constraint from label text — derive kind and credit values from hourscol
                if (mHR.find()) {
                    kind = "CREDIT_AT_LEAST";
                    b.creditMin(new BigDecimal(mHR.group(1))).creditMax(new BigDecimal(mHR.group(2)));
                } else if (mHS.find()) {
                    try {
                        BigDecimal v = new BigDecimal(mHS.group(1));
                        if (v.signum() > 0) { kind = "CREDIT_AT_LEAST"; b.creditMin(v); }
                    } catch (NumberFormatException ignore) { /* safe */ }
                }
            } else if ("N_OF".equals(kind) || "ANY_OF".equals(kind) || "LEVEL_AT_LEAST".equals(kind)) {
                // Store total expected hours as supplementary data on the selection node
                if (mHR.find()) {
                    b.creditMin(new BigDecimal(mHR.group(1))).creditMax(new BigDecimal(mHR.group(2)));
                } else if (mHS.find()) {
                    try {
                        BigDecimal v = new BigDecimal(mHS.group(1));
                        if (v.signum() > 0) b.creditMin(v);
                    } catch (NumberFormatException ignore) { /* safe */ }
                }
            }
        }

        applyFootnotesFromRow(tr, footnoteMap, b, usedFootnotes);
        return b.kind(kind).build();
    }

    private boolean isCourseRow(Element tr) {
        return tr.selectFirst("a.bubblelink.code") != null || tr.selectFirst("td.codecol") != null;
    }

    private ParsedReqNode buildCourseNodeFromRow(
            Elements allRows,
            int idx,
            List<Footnote> footnoteMap,
            Set<Element> usedFootnotes
    ) {
        Element baseRow = allRows.get(idx);
        if (baseRow.className().contains("orclass")) return null; // consumed by previous non-or row

        // Build OR group: the row + subsequent .orclass rows
        List<Element> group = new ArrayList<>();
        group.add(baseRow);

        int j = idx + 1;
        while (j < allRows.size()) {
            Element next = allRows.get(j);
            if (next.className().contains("orclass")) {
                group.add(next);
                j++;
            } else break;
        }

        if (group.size() == 1) {
            return buildLeafCoursesNodeFromRow(baseRow, footnoteMap, usedFootnotes);
        }

        ParsedReqNode anyOf = ParsedReqNode.builder()
                .name(buildOrGroupLabel(group))
                .kind("ANY_OF")
                .sourceRef(baseRow.cssSelector())
                .children(new ArrayList<>())
                .build();

        for (Element row : group) {
            ParsedReqNode leaf = buildLeafCoursesNodeFromRow(row, footnoteMap, usedFootnotes);
            if (leaf != null) anyOf.getChildren().add(leaf);
        }

        return anyOf.getChildren() != null && !anyOf.getChildren().isEmpty() ? anyOf : null;
    }

    private String buildOrGroupLabel(List<Element> group) {
        Element first = group.getFirst();
        Element titleCell = first.selectFirst("td.titlecol");
        if (titleCell != null) return "One of: " + normalize(titleCell.text());
        return "One of multiple options";
    }

    private ParsedReqNode buildLeafCoursesNodeFromRow(
            Element tr,
            List<Footnote> footnoteMap,
            Set<Element> usedFootnotes
    ) {
        Element codeCell = tr.selectFirst("td.codecol");
        if (codeCell == null) return null;

        Elements links = codeCell.select("a.bubblelink.code");

        // Multiple anchors in a single cell (paired labs/lectures) -> ALL_OF composite
        if (links.size() > 1 && !tr.className().contains("orclass")) {
            ParsedReqNode.ParsedReqNodeBuilder compositeBuilder = ParsedReqNode.builder()
                    .name(normalize(codeCell.text()))
                    .kind("ALL_OF")
                    .sourceRef(tr.cssSelector())
                    .children(new ArrayList<>())
                    .doubleCountAllowed(Boolean.TRUE);
            applyFootnotesFromRow(tr, footnoteMap, compositeBuilder, usedFootnotes);
            ParsedReqNode composite = compositeBuilder.build();
            for (Element l : links) {
                String text = normalize(l.text());
                ParsedReqNode leaf = buildSingleCourseLeaf(text, tr.cssSelector());
                if (leaf != null) composite.getChildren().add(leaf);
            }
            return composite.getChildren().isEmpty() ? null : composite;
        }

        Element link = links.first();
        String codeText = normalize(link != null ? link.text() : codeCell.text());
        if (codeText.isEmpty()) return null;

        List<ParsedReqNodeCourseOption> options = courseLookupService.extractCourseCodes(codeText);
        if (options == null || options.isEmpty()) {
            // Some bullets (e.g., headings without codes) can land here—skip quietly
            log.debug("Could not parse course codes from '{}'", codeText);
            return null;
        }

        ParsedReqNode.ParsedReqNodeBuilder builder = ParsedReqNode.builder()
                .name(codeText)
                .kind("LEAF_COURSES")
                .sourceRef(tr.cssSelector())
                .courseOptions(new ArrayList<>(options))
                .doubleCountAllowed(Boolean.TRUE);
        applyFootnotesFromRow(tr, footnoteMap, builder, usedFootnotes);
        return builder.build();
    }

    private boolean isTotalHoursRow(Element tr) {
        Element td = tr.selectFirst("td");
        if (td == null) return false;
        String text = normalize(td.text()).toLowerCase(Locale.ROOT);
        return text.contains("total credit hours") || text.contains("total hours");
    }

    private ParsedReqNode parseTotalHoursRow(Element tr) {
        Element labelCell = tr.selectFirst("td:first-child");
        Element hoursCell = tr.selectFirst("td.hourscol");
        if (labelCell == null || hoursCell == null) return null;

        String label = normalize(labelCell.text());
        String hoursText = normalize(hoursCell.text());

        ParsedReqNode.ParsedReqNodeBuilder b = ParsedReqNode.builder()
                .name(label)
                .kind("CREDIT_AT_LEAST")
                .sourceRef(tr.cssSelector());

        Matcher mRange = HOURS_RANGE_PATTERN.matcher(hoursText);
        Matcher mSingle = HOURS_SINGLE_PATTERN.matcher(hoursText);

        if (mRange.find()) {
            b.creditMin(new BigDecimal(mRange.group(1)))
                    .creditMax(new BigDecimal(mRange.group(2)));
        } else if (mSingle.find()) {
            try {
                b.creditMin(new BigDecimal(mSingle.group(1)));
            } catch (NumberFormatException ignore) { /* safe */ }
        }
        return b.build();
    }

    /* ==============================
       Pruning / Validation
       ============================== */

    /**
     * Deep-prunes the tree:
     *  - removes null children
     *  - prunes empty nodes (no children AND no courseOptions), except CREDIT_AT_LEAST nodes
     */
    private void pruneTree(ParsedReqNode node) {
        if (node == null) return;

        if (node.getChildren() == null) node.setChildren(new ArrayList<>());
        node.getChildren().removeIf(Objects::isNull);

        // Recurse first so we prune from leaves upward
        for (ParsedReqNode child : new ArrayList<>(node.getChildren())) {
            pruneTree(child);
        }

        // Remove any children that ended up empty and are not constraint nodes
        node.getChildren().removeIf(this::isEffectivelyEmpty);
    }

    /**
     * Empty = no children AND no courseOptions, unless it's a standalone constraint node.
     * Container kinds (ALL_OF, ANY_OF, N_OF, LEAF_COURSES, etc.) with no content are always
     * considered empty and will be pruned, even if they carry metadata like nRequired.
     */
    private boolean isEffectivelyEmpty(ParsedReqNode n) {
        boolean noChildren = (n.getChildren() == null || n.getChildren().isEmpty());
        boolean noOptions = (n.getCourseOptions() == null || n.getCourseOptions().isEmpty());
        // Has content → not empty
        if (!noChildren || !noOptions) return false;
        // Standalone constraint nodes carry meaning without children/options
        return switch (n.getKind()) {
            case "CREDIT_AT_LEAST", "LEVEL_AT_LEAST", "COURSE_CAP", "GRADE_MIN", "RESIDENCY_MIN",
                 "GPA_AT_LEAST", "DOUBLE_COUNT_RULE", "EXPERIENCE", "CONCURRENCY_BLOCK" -> false;
            case "NOTE_ONLY" -> n.getNotes() == null || n.getNotes().isBlank();
            // Container/selector nodes (ALL_OF, ANY_OF, N_OF, LEAF_COURSES, …) with no content
            default -> true;
        };
    }

    /* ==============================
       Utils
       ============================== */

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ')
                .replace('\u2013', '-')  // en dash
                .replace('\u2014', '-')  // em dash
                .replace('\u2215', '/')  // division slash
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String cleanHeaderLabel(String label) {
        // Remove trailing parentheticals "(...)" and trim
        return label.replaceAll("\\s*\\(.*\\)$", "").trim();
    }

    private static String safeLocation(Document doc) {
        try { return doc.location(); } catch (Exception ignored) { return "(unknown)"; }
    }

    private String findTableLabel(Element table) {
        Element caption = table.selectFirst("caption");
        if (caption != null) {
            String captionText = normalize(caption.text());
            if (!captionText.isEmpty() && !"course list".equalsIgnoreCase(captionText)) {
                return captionText;
            }
        }

        Element prev = table.previousElementSibling();
        while (prev != null) {
            String tag = prev.tagName();
            if (tag.matches("h[1-4]")) {
                String text = normalize(prev.text());
                if (!text.isEmpty()) return text;
            }
            // Stop at another course-list table: a table that has no heading between it and us
            // should not donate its heading to us.
            if ("table".equals(tag) && prev.hasClass("sc_courselist")) break;
            prev = prev.previousElementSibling();
        }
        return null;
    }

    private List<ParsedReqNode> extractNarrativeRequirements(
            Element textContainer,
            List<Footnote> footnoteMap,
            Set<Element> usedFootnotes
    ) {
        List<ParsedReqNode> out = new ArrayList<>();
        if (textContainer == null) return out;

        List<Element> blocks = textContainer.select("p, li");
        for (Element el : blocks) {
            if (el.closest("table.sc_courselist") != null) continue;
            if (el.closest("dl.sc_footnotes") != null) continue;
            String text = normalize(el.text());
            if (text.isEmpty() || !looksLikeRequirementText(text)) continue;

            ParsedReqNode.ParsedReqNodeBuilder builder = ParsedReqNode.builder()
                    .name(text)
                    .kind("NOTE_ONLY")
                    .notes(text)
                    .sourceRef(el.cssSelector())
                    .sourceUrl(safeLocation(el.ownerDocument()));
            applyDoubleCountFromText(text, builder);
            applyFootnotesFromRow(el, footnoteMap, builder, usedFootnotes);
            out.add(builder.build());
        }

        return out;
    }

    private boolean looksLikeRequirementText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("must")
                || lower.contains("required")
                || lower.contains("requirement")
                || lower.contains("select")
                || lower.contains("choose")
                || lower.contains("take")
                || lower.contains("minimum")
                || lower.contains("at least")
                || lower.contains("no more than")
                || lower.contains("credit")
                || lower.contains("hour")
                || lower.contains("grade");
    }

    private void applyDoubleCountFromText(String text, ParsedReqNode.ParsedReqNodeBuilder builder) {
        if (builder == null) return;
        BigDecimal cap = extractDoubleCountCap(text);
        if (cap != null) {
            builder.doubleCountCap(cap);
        }
        if (isDoubleCountRule(text)) {
            builder.doubleCountAllowed(Boolean.FALSE);
        }
    }

    private BigDecimal extractDoubleCountCap(String text) {
        if (text == null) return null;
        String normalized = normalize(text);
        if (normalized.isEmpty()) return null;
        Matcher matcher = DOUBLE_COUNT_CAP_PATTERN.matcher(normalized);
        if (!matcher.find()) return null;
        return parseNumberToken(matcher.group(1));
    }

    private boolean isDoubleCountRule(String text) {
        if (text == null) return false;
        String normalized = normalize(text);
        if (normalized.isEmpty()) return false;
        if (DOUBLE_COUNT_DENY_PATTERN.matcher(normalized).find()) return true;
        if (DOUBLE_COUNT_BOTH_DENY_PATTERN.matcher(normalized).find()) return true;
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("only be used once")
                || lower.contains("only be counted once")
                || lower.contains("only count once")
                || lower.contains("may only be used once")
                || lower.contains("can only be used once")
                || lower.contains("may only be counted once")
                || lower.contains("may only count once")
                || lower.contains("may not be counted twice")
                || lower.contains("may only be used to fulfill one")
                || lower.contains("may only be used to satisfy one")
                || lower.contains("may only be used to meet one")
                || lower.contains("may only be used for one requirement")
                || lower.contains("may only count toward one requirement")
                || lower.contains("cannot be used in the core")
                || lower.contains("cannot be used in the major");
    }

    private void collectRetainedFootnotes(ParsedReqNode node, Set<String> retained) {
        if (node.getFootnoteText() != null) retained.addAll(List.of(node.getFootnoteText().split("\\n")));
        if (node.getChildren() != null) for (ParsedReqNode child : node.getChildren()) collectRetainedFootnotes(child, retained);
    }

    private record Footnote(int number, String text, Element source) { }

    private List<Footnote> buildFootnoteMap(Element textContainer) {
        List<Footnote> out = new ArrayList<>();
        if (textContainer == null) return out;
        for (Element dt : textContainer.select("dl.sc_footnotes > dt")) {
            Element dd = dt.nextElementSibling();
            if (dd == null || !"dd".equalsIgnoreCase(dd.tagName())) continue;
            Integer num = parseFootnoteNumber(dt.text());
            String text = normalize(dd.text());
            if (num != null && !text.isEmpty()) out.add(new Footnote(num, text, dd));
        }
        return out;
    }

    // Catalog sections can restart numbering at 1. Resolve against the nearest
    // following definition list, rather than overwriting a page-wide number map.
    private Footnote resolveFootnote(Element element, int number, List<Footnote> footnotes) {
        Elements order = element.ownerDocument().getAllElements();
        int position = order.indexOf(element);
        Element list = null;
        for (Footnote footnote : footnotes) {
            if (order.indexOf(footnote.source()) > position) {
                list = footnote.source().parent();
                break;
            }
        }
        if (list == null && !footnotes.isEmpty()) list = footnotes.getLast().source().parent();
        for (Footnote footnote : footnotes) {
            if (footnote.source().parent() == list && footnote.number() == number) return footnote;
        }
        return null;
    }

    private Integer parseFootnoteNumber(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("\\d+").matcher(text);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private List<Integer> extractFootnoteRefs(Element row) {
        if (row == null) return List.of();
        Set<Integer> refs = new LinkedHashSet<>();
        for (Element sup : row.select("sup")) {
            Matcher m = Pattern.compile("\\d+").matcher(sup.text());
            while (m.find()) {
                try {
                    refs.add(Integer.parseInt(m.group()));
                } catch (NumberFormatException ignore) {
                    // skip invalid refs
                }
            }
        }
        return new ArrayList<>(refs);
    }

    private void applyFootnotesFromRow(
            Element element,
            List<Footnote> footnoteMap,
            ParsedReqNode.ParsedReqNodeBuilder builder,
            Set<Element> usedFootnotes
    ) {
        if (element == null || builder == null || footnoteMap == null || footnoteMap.isEmpty()) return;
        List<Integer> refs = extractFootnoteRefs(element);
        if (refs.isEmpty()) return;
        Set<String> matched = new LinkedHashSet<>();
        for (Integer ref : refs) {
            Footnote footnote = resolveFootnote(element, ref, footnoteMap);
            if (footnote == null) continue;
            String text = footnote.text();
            applyDoubleCountFromText(text, builder);
            matched.add(text);
            if (usedFootnotes != null) {
                usedFootnotes.add(footnote.source());
            }
        }
        if (!matched.isEmpty()) {
            builder.footnoteText(String.join("\n", matched));
        }
    }

    private boolean containsComment(Element tr) {
        return tr.selectFirst(".courselistcomment") != null;
    }

    private String extractCommentText(Element tr) {
        Element commentEl = tr.selectFirst(".courselistcomment");
        if (commentEl == null) commentEl = tr.selectFirst("td");
        return normalize(commentEl != null ? commentEl.text() : "");
    }

    private boolean isConnectorRow(Element tr) {
        if (!containsComment(tr)) return false;
        return parseConnector(extractCommentText(tr)) != null;
    }

    private String parseConnector(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        if ("or".equals(lower)) return "OR";
        if ("and".equals(lower)) return "AND";
        return null;
    }

    private boolean isSelectorComment(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("take") && lower.contains("no more than")) return false;
        return lower.startsWith("select")
                || lower.startsWith("choose")
                || lower.startsWith("take")
                || lower.startsWith("complete")
                || lower.contains("must complete")
                || lower.contains("take both");
    }

    private ParsedReqNode buildNodeFromComment(
            Element tr,
            List<Footnote> footnoteMap,
            Set<Element> usedFootnotes
    ) {
        Element comment = tr.selectFirst(".courselistcomment");
        if (comment == null) comment = tr.selectFirst("td");
        if (comment == null) return null;

        String text = normalize(comment.text());
        if (text.isEmpty()) return null;

        if (TRACK_OPTION_PATTERN.matcher(text).find()) {
            ParsedReqNode.ParsedReqNodeBuilder trackBuilder = ParsedReqNode.builder()
                    .name(cleanHeaderLabel(text))
                    .kind("TRACK_OPTION")
                    .notes(text)
                    .sourceRef(tr.cssSelector())
                    .children(new ArrayList<>());
            applyFootnotesFromRow(tr, footnoteMap, trackBuilder, usedFootnotes);
            return trackBuilder.build();
        }

        ParsedReqNode.ParsedReqNodeBuilder b = ParsedReqNode.builder()
                .name(cleanHeaderLabel(text))
                .kind("ALL_OF")
                .sourceRef(tr.cssSelector())
                .children(new ArrayList<>());

        String lower = text.toLowerCase(Locale.ROOT);
        String kind = "ALL_OF";
        boolean explicitCount = false;
        boolean selectorLead = isSelectorComment(text);

        if (SELECT_ALL_PATTERN.matcher(text).find() || SELECT_THE_FOLLOWING_PATTERN.matcher(text).find()) {
            explicitCount = true;
        }

        boolean hasHoursWord = lower.contains("hour") || lower.contains("credit");
        Matcher selectCount = SELECT_COUNT_PATTERN.matcher(text);
        boolean durationCount = text.matches("(?is).*(?:select|choose|complete|take)\\s+" + COUNT_TOKEN_PATTERN
                + "\\s*(?:\\(\\d+\\)\\s*)?(?:semesters?|years?|weeks?|months?)\\b.*");
        boolean countParsed = false;
        if (selectCount.find()) {
            boolean explicitCourses = selectCount.group(4) != null;
            if ((!hasHoursWord && !durationCount) || explicitCourses) {
                Integer count = parseCountToken(selectCount.group(3));
                if (count != null) {
                    boolean atLeast = selectCount.group(2) != null && !selectCount.group(2).isBlank();
                    if (count == 1 && !atLeast) {
                        kind = "ANY_OF";
                        b.nRequired(1);
                    } else {
                        kind = "N_OF";
                        b.nRequired(count);
                    }
                    explicitCount = true;
                    countParsed = true;
                    if (atLeast) {
                        b.notes(text);
                    }
                }
            }
        }
        if (!countParsed && !hasHoursWord && !durationCount && SELECT_ONE_PATTERN.matcher(text).find()) {
            kind = "ANY_OF";
            b.nRequired(1);
            explicitCount = true;
        }
        if (TAKE_BOTH_PATTERN.matcher(text).find()) {
            kind = "ALL_OF";
            explicitCount = true;
        }

        if (!explicitCount && selectorLead && !durationCount && "ALL_OF".equals(kind)) {
            kind = "ANY_OF";
        }

        BigDecimal creditMin = null;
        BigDecimal creditMax = null;
        Matcher selectHoursRange = SELECT_HOURS_RANGE_PATTERN.matcher(text);
        Matcher selectHoursSingle = SELECT_HOURS_SINGLE_PATTERN.matcher(text);
        Matcher selectHoursUpTo = SELECT_HOURS_UP_TO_PATTERN.matcher(text);
        Matcher selectHoursToEqual = SELECT_HOURS_TO_EQUAL_PATTERN.matcher(text);
        if (selectHoursRange.find()) {
            creditMin = parseNumberToken(selectHoursRange.group(2));
            creditMax = parseNumberToken(selectHoursRange.group(3));
        } else if (selectHoursSingle.find()) {
            creditMin = parseNumberToken(selectHoursSingle.group(2));
        } else if (selectHoursUpTo.find()) {
            creditMax = parseNumberToken(selectHoursUpTo.group(2));
        } else if (selectHoursToEqual.find()) {
            creditMin = parseNumberToken(selectHoursToEqual.group(1));
        }

        Element hoursCell = tr.selectFirst("td.hourscol");
        String hoursText = normalize(hoursCell != null ? hoursCell.text() : "");
        if (!hoursText.isEmpty()) {
            Matcher mRange = HOURS_RANGE_PATTERN.matcher(hoursText);
            Matcher mSingle = HOURS_SINGLE_PATTERN.matcher(hoursText);
            if (creditMin == null && creditMax == null) {
                if (mRange.find()) {
                    creditMin = toBigDecimalSafe(mRange.group(1));
                    creditMax = toBigDecimalSafe(mRange.group(2));
                } else if (mSingle.find()) {
                    creditMin = toBigDecimalSafe(mSingle.group(1));
                }
            } else if (creditMax == null && mRange.find()) {
                creditMax = toBigDecimalSafe(mRange.group(2));
            }
        }

        if (creditMin != null) b.creditMin(creditMin);
        if (creditMax != null) b.creditMax(creditMax);

        if (!explicitCount && (creditMin != null || creditMax != null)
                && ("ALL_OF".equals(kind) || "ANY_OF".equals(kind))) {
            kind = "CREDIT_AT_LEAST";
        }

        boolean matchedLevelCap = false;
        Matcher levelCapHours = LEVEL_CAP_HOURS_PATTERN.matcher(text);
        if (levelCapHours.find()) {
            BigDecimal cap = toBigDecimalSafe(levelCapHours.group(1));
            Integer lvl = parseLevelSafe(levelCapHours.group(2));
            if (cap != null && lvl != null) {
                applyLevelCapHours(b, lvl, cap);
                matchedLevelCap = true;
                if (!explicitCount && "ALL_OF".equals(kind) && creditMin == null && creditMax == null) {
                    kind = "COURSE_CAP";
                }
            }
        }

        Matcher levelCapCourses = LEVEL_CAP_COURSES_PATTERN.matcher(text);
        if (levelCapCourses.find()) {
            Integer cap = parseCountToken(levelCapCourses.group(1));
            Integer lvl = parseLevelSafe(levelCapCourses.group(2));
            if (cap != null && lvl != null) {
                applyLevelCapCourses(b, lvl, cap);
                matchedLevelCap = true;
                if (!explicitCount && "ALL_OF".equals(kind) && creditMin == null && creditMax == null) {
                    kind = "COURSE_CAP";
                }
            }
        }

        Matcher levelHeader = LEVEL_HEADER_PATTERN.matcher(text);
        if (!matchedLevelCap && !lower.contains("no more than") && levelHeader.find()) {
            Integer lvl = parseLevelSafe(levelHeader.group(1));
            if (lvl != null) {
                if ("ALL_OF".equals(kind) && !explicitCount && creditMin == null && creditMax == null) {
                    kind = "LEVEL_AT_LEAST";
                }
                b.levelMin(lvl);
            }
        }

        Matcher groupCap = COURSE_GROUP_CAP_PATTERN.matcher(text);
        if (!matchedLevelCap && groupCap.find() && !hasHoursWord && !lower.contains("level")) {
            Integer cap = parseCountToken(groupCap.group(1));
            if (cap != null) {
                kind = "COURSE_CAP";
                b.nRequired(cap);
                explicitCount = true;
            }
        }

        applyDoubleCountFromText(text, b);
        applyFootnotesFromRow(tr, footnoteMap, b, usedFootnotes);

        ParsedReqNode node = b.kind(kind).build();
        if (!explicitCount && "ALL_OF".equals(node.getKind()) && node.getNRequired() == null
                && node.getCreditMin() == null && node.getCreditMax() == null
                && node.getLevelMin() == null
                && node.getLevelCap100() == null && node.getLevelCap200() == null
                && node.getLevelCap300() == null && node.getLevelCap400() == null
                && node.getLevelCap100Courses() == null && node.getLevelCap200Courses() == null
                && node.getLevelCap300Courses() == null && node.getLevelCap400Courses() == null
        ) {
            node.setKind("NOTE_ONLY");
            node.setNotes(text);
        }
        return node;
    }

    private boolean isTrackOptionHeader(String text, boolean inTrackGroup) {
        if (text == null || text.isBlank()) return false;
        if (TRACK_OPTION_PATTERN.matcher(text).find()) return true;
        return inTrackGroup && TRACK_OPTION_INDEX_PATTERN.matcher(text).find();
    }

    private ParsedReqNode buildTrackOptionNode(
            String text,
            Element tr,
            List<Footnote> footnoteMap,
            Set<Element> usedFootnotes
    ) {
        String label = cleanHeaderLabel(text);
        ParsedReqNode.ParsedReqNodeBuilder trackBuilder = ParsedReqNode.builder()
                .name(label)
                .kind("TRACK_OPTION")
                .notes(text)
                .sourceRef(tr.cssSelector())
                .children(new ArrayList<>());
        applyFootnotesFromRow(tr, footnoteMap, trackBuilder, usedFootnotes);
        return trackBuilder.build();
    }

    private List<ParsedReqNode> ensureChildren(ParsedReqNode node) {
        if (node.getChildren() == null) {
            node.setChildren(new ArrayList<>());
        }
        return node.getChildren();
    }

    private Integer parseCountToken(String token) {
        if (token == null) return null;
        String lower = token.trim().toLowerCase(Locale.ROOT);
        switch (lower) {
            case "one": return 1;
            case "two": return 2;
            case "three": return 3;
            case "four": return 4;
            case "five": return 5;
            case "six": return 6;
            case "seven": return 7;
            case "eight": return 8;
            case "nine": return 9;
            case "ten": return 10;
            case "eleven": return 11;
            case "twelve": return 12;
            default:
                try {
                    return Integer.parseInt(lower);
                } catch (NumberFormatException ignore) {
                    return null;
                }
        }
    }

    private BigDecimal parseNumberToken(String token) {
        if (token == null) return null;
        BigDecimal numeric = toBigDecimalSafe(token);
        if (numeric != null) return numeric;
        Integer count = parseCountToken(token);
        return count != null ? BigDecimal.valueOf(count) : null;
    }

    private Integer parseLevelSafe(String token) {
        if (token == null) return null;
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private BigDecimal toBigDecimalSafe(String token) {
        if (token == null) return null;
        try {
            return new BigDecimal(token);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private void applyLevelCapHours(ParsedReqNode.ParsedReqNodeBuilder b, int level, BigDecimal cap) {
        switch (level) {
            case 100 -> b.levelCap100(cap);
            case 200 -> b.levelCap200(cap);
            case 300 -> b.levelCap300(cap);
            case 400 -> b.levelCap400(cap);
            default -> { }
        }
    }

    private void applyLevelCapCourses(ParsedReqNode.ParsedReqNodeBuilder b, int level, Integer cap) {
        switch (level) {
            case 100 -> b.levelCap100Courses(cap);
            case 200 -> b.levelCap200Courses(cap);
            case 300 -> b.levelCap300Courses(cap);
            case 400 -> b.levelCap400Courses(cap);
            default -> { }
        }
    }

    private ParsedReqNode flushExpression(ExpressionState expr, ParsedReqNode currentSection, ParsedReqNode lastAdded) {
        if (expr == null || !expr.isActive()) return lastAdded;
        if (currentSection == null) {
            expr.reset();
            return lastAdded;
        }
        ParsedReqNode exprNode = buildExpressionNode(expr.terms, expr.ops);
        if (exprNode != null) {
            currentSection.getChildren().add(exprNode);
            lastAdded = exprNode;
        }
        expr.reset();
        return lastAdded;
    }

    private ParsedReqNode buildExpressionNode(List<ParsedReqNode> terms, List<String> ops) {
        if (terms == null || terms.isEmpty()) return null;
        if (terms.size() == 1) return terms.getFirst();
        if (ops == null || ops.size() != terms.size() - 1) {
            ParsedReqNode all = ParsedReqNode.builder()
                    .name("All of the following")
                    .kind("ALL_OF")
                    .children(new ArrayList<>(terms))
                    .build();
            return all;
        }

        List<ParsedReqNode> orTerms = new ArrayList<>();
        ParsedReqNode current = terms.getFirst();
        for (int i = 0; i < ops.size(); i++) {
            String op = ops.get(i);
            ParsedReqNode next = terms.get(i + 1);
            if ("AND".equals(op)) {
                current = mergeAllOf(current, next);
            } else {
                orTerms.add(current);
                current = next;
            }
        }
        orTerms.add(current);

        if (orTerms.size() == 1) return orTerms.getFirst();

        return ParsedReqNode.builder()
                .name("One of multiple options")
                .kind("ANY_OF")
                .children(orTerms)
                .build();
    }

    private ParsedReqNode mergeAllOf(ParsedReqNode left, ParsedReqNode right) {
        ParsedReqNode all = ParsedReqNode.builder()
                .name("All of the following")
                .kind("ALL_OF")
                .children(new ArrayList<>())
                .build();
        all.getChildren().add(left);
        all.getChildren().add(right);
        return all;
    }

    private ParsedReqNode buildSingleCourseLeaf(String codeText, String sourceRef) {
        List<ParsedReqNodeCourseOption> options = courseLookupService.extractCourseCodes(codeText);
        if (options == null || options.isEmpty()) return null;
        return ParsedReqNode.builder()
                .name(codeText)
                .kind("LEAF_COURSES")
                .sourceRef(sourceRef)
                .courseOptions(new ArrayList<>(options))
                .build();
    }

    private static final class ExpressionState {
        private final List<ParsedReqNode> terms = new ArrayList<>();
        private final List<String> ops = new ArrayList<>();
        private String pendingConnector;

        private void start(ParsedReqNode first) {
            if (first != null) terms.add(first);
        }

        private boolean isActive() {
            return !terms.isEmpty();
        }

        private void reset() {
            terms.clear();
            ops.clear();
            pendingConnector = null;
        }
    }
}
