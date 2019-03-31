package chopper.server;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Search {

    private static final int LEN = 70;

    static class Sect {
        final String fileName;
        final String captionPath;
        final String captionName;
        final String text;
        final String lcText;

        public Sect(String fileName, String captionPath, String captionName, String text) {
            this.fileName = fileName;
            this.captionPath = captionPath;
            this.captionName = captionName;
            this.text = text;
            this.lcText = text.toLowerCase();
        }
    }

    private final List<Sect> sections = new ArrayList<>();

    public Search(InputStream inputStream) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            buffer.lines().forEach(s -> {
                String[] parts = s.split("\t");
                sections.add(new Sect(parts[0], parts[1], parts[2], parts.length == 4 ? parts[3] : ""));
            });
        }
        inputStream.close();
    }

    public Search(List<Sect> sections) {
        this.sections.addAll(sections);
    }

    public List<SearchResult> search(String term, boolean caseSensitive) {
        List<SearchResult> results = new ArrayList<>();

        term = term.trim();
        boolean exactMatch = false;
        if (term.startsWith("\"") && term.endsWith("")) {
            term = term.substring(1, term.length() - 1);
            exactMatch = true;
        }
        if (term.length() < 1) {
            return results;
        }

        for (Sect sect : sections) {
            String text;
            if (!caseSensitive) {
                term = term.toLowerCase();
                text = sect.lcText;
            } else {
                text = sect.text;
            }
            int idx = text.indexOf(term);
            if (idx > -1) {
                SearchResult result = new SearchResult(sect.fileName, sect.captionPath, sect.captionName, text);

                String caption = caseSensitive ? sect.captionName : sect.captionName.toLowerCase();
                result.setCaptionWeight(StringUtils.countMatches(caption, term));

                int start = 0;
                while (idx > -1) {
                    StringBuilder hit = new StringBuilder("<div class=\"hit-info\">");
                    hit.append(StringEscapeUtils.escapeHtml4(abbreviateStart(sect.text, idx)));
                    int hitEnd = idx + term.length();
                    hit.append("<span class=\"hit\">")
                            .append(StringEscapeUtils.escapeHtml4(sect.text.substring(idx, hitEnd)))
                            .append("</span>");
                    hit.append(StringEscapeUtils.escapeHtml4(abbreviateEnd(sect.text, hitEnd)));
                    hit.append("</div>");

                    result.hits.add(hit.toString());

                    start = idx + 1;
                    idx = text.indexOf(term, start);
                }

                result.setBodyWeight(result.hits.size());

                if (result.hits.size() > 10) {
                    int more = result.hits.size() - 10;
                    while (result.hits.size() > 10) {
                        result.hits.remove(result.hits.size() - 1);
                    }
                    result.hits.add("<div class=\"hit-info\">" + "And " + more + " more" + "</div>");
                }

                results.add(result);
            }
        }

        results.sort(null);

        if (!exactMatch && term.contains(" ")) {
            List<SearchResult> allPartResults = new ArrayList<>();
            String[] termParts = StringUtils.split(term);
            for (String part : termParts) {
                List<SearchResult> partResults = search(part, caseSensitive);
                partResults.stream()
                        .filter(partResult -> results.stream()
                                .noneMatch(searchResult -> searchResult.captionPath.equals(partResult.captionPath)))
                        .filter(partResult -> allPartResults.stream()
                                .noneMatch(searchResult -> searchResult.captionPath.equals(partResult.captionPath)))
                        .forEach(allPartResults::add);
            }

            for (SearchResult partResult : allPartResults) {
                String caption = caseSensitive ? partResult.captionName : partResult.captionName.toLowerCase();
                int captionMultiplier = 1;
                int bodyMultiplier = 1;
                for (String termPart : termParts) {
                    if (!caseSensitive)
                        termPart = termPart.toLowerCase();
                    if (caption.contains(termPart))
                        captionMultiplier++;
                    if (partResult.body.contains(termPart))
                        bodyMultiplier++;
                }
                partResult.setCaptionWeight(partResult.getCaptionWeight() * captionMultiplier);
                partResult.setBodyWeight(partResult.getBodyWeight() * bodyMultiplier);
            }

            allPartResults.sort(null);
            results.addAll(allPartResults);
        }

        return results;
    }

    private String abbreviateStart(String text, int idx) {
        if (idx - LEN <= 0) {
            return text.substring(0, idx);
        } else {
            int startIdx = idx - LEN;
            while (text.charAt(startIdx) != ' ' && startIdx < idx) {
                startIdx++;
            }
            return "..." + text.substring(startIdx, idx);
        }
    }

    private String abbreviateEnd(String text, int idx) {
        if (idx + LEN >= text.length()) {
            return text.substring(idx);
        } else {
            int endIdx = idx + LEN;
            while (text.charAt(endIdx) != ' ' && endIdx > idx) {
                endIdx--;
            }
            return text.substring(idx, endIdx) + "...";
        }
    }
}