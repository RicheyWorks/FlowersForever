package com.flowerfarm.service;

import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.repository.CustomerOrderRepository;
import com.flowerfarm.repository.CustomerRepository;
import com.flowerfarm.repository.FieldScienceExportRepository;
import com.flowerfarm.repository.HarvestRepository;
import com.flowerfarm.repository.InventoryRepository;
import com.flowerfarm.repository.KnowledgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transactional workflows and plain-language search for farm knowledge. */
@Service
public class KnowledgeService {

    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 500;
    private static final long MAX_QUERY_DAYS = 3_660;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_-]+");
    private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "did", "do", "does", "during", "for", "from",
            "how", "in", "is", "it", "last", "of", "on", "our", "that", "the", "this",
            "to", "was", "we", "were", "what", "when", "where", "which", "who", "with",
            "work", "worked", "working");
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("january", Month.JANUARY), Map.entry("jan", Month.JANUARY),
            Map.entry("february", Month.FEBRUARY), Map.entry("feb", Month.FEBRUARY),
            Map.entry("march", Month.MARCH), Map.entry("mar", Month.MARCH),
            Map.entry("april", Month.APRIL), Map.entry("apr", Month.APRIL),
            Map.entry("may", Month.MAY),
            Map.entry("june", Month.JUNE), Map.entry("jun", Month.JUNE),
            Map.entry("july", Month.JULY), Map.entry("jul", Month.JULY),
            Map.entry("august", Month.AUGUST), Map.entry("aug", Month.AUGUST),
            Map.entry("september", Month.SEPTEMBER), Map.entry("sep", Month.SEPTEMBER),
            Map.entry("sept", Month.SEPTEMBER),
            Map.entry("october", Month.OCTOBER), Map.entry("oct", Month.OCTOBER),
            Map.entry("november", Month.NOVEMBER), Map.entry("nov", Month.NOVEMBER),
            Map.entry("december", Month.DECEMBER), Map.entry("dec", Month.DECEMBER));

    private final KnowledgeRepository repository;
    private final HarvestRepository harvestRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final FieldScienceExportRepository fieldScienceExportRepository;
    private final SyncHistoryService syncHistoryService;
    private final Clock clock;

    @Autowired
    public KnowledgeService(KnowledgeRepository repository,
                            HarvestRepository harvestRepository,
                            InventoryRepository inventoryRepository,
                            CustomerOrderRepository orderRepository,
                            CustomerRepository customerRepository,
                            SyncHistoryService syncHistoryService,
                            FieldScienceExportRepository fieldScienceExportRepository,
                            FarmProfile farmProfile) {
        this(repository, harvestRepository, inventoryRepository, orderRepository,
                customerRepository, syncHistoryService, fieldScienceExportRepository,
                Clock.system(farmProfile.zoneId()));
    }

    KnowledgeService(KnowledgeRepository repository,
                     HarvestRepository harvestRepository,
                     InventoryRepository inventoryRepository,
                     CustomerOrderRepository orderRepository,
                     CustomerRepository customerRepository,
                     SyncHistoryService syncHistoryService,
                     Clock clock) {
        this(repository, harvestRepository, inventoryRepository, orderRepository,
                customerRepository, syncHistoryService, null, clock);
    }

    KnowledgeService(KnowledgeRepository repository,
                     HarvestRepository harvestRepository,
                     InventoryRepository inventoryRepository,
                     CustomerOrderRepository orderRepository,
                     CustomerRepository customerRepository,
                     SyncHistoryService syncHistoryService,
                     FieldScienceExportRepository fieldScienceExportRepository,
                     Clock clock) {
        this.repository = repository;
        this.harvestRepository = harvestRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.fieldScienceExportRepository = fieldScienceExportRepository;
        this.syncHistoryService = syncHistoryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<KnowledgeEntry> findById(Long id) {
        return repository.findById(id);
    }

    /** Small database-bounded field journal for the phone and tablet workflow. */
    @Transactional(readOnly = true)
    public List<KnowledgeEntry> recentThrough(LocalDate through, int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Field note limit must be between 1 and 50.");
        }
        LocalDate date = through == null ? LocalDate.now(clock) : through;
        return List.copyOf(repository.findRecentThrough(date, limit));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeEntry> search(SearchRequest request) {
        SearchRequest safe = request == null ? SearchRequest.all() : request;
        int limit = requireLimit(safe.limit());
        validateDateRange(safe.from(), safe.to());

        PlainQuery plain = understand(safe.query());
        LocalDate from = safe.from() != null ? safe.from() : plain.from();
        LocalDate to = safe.to() != null ? safe.to() : plain.to();
        validateDateRange(from, to);
        String crop = lower(safe.crop());
        String bed = lower(safe.bedOrField());
        String tag = normalizeLookupTag(safe.tag());
        LocalDate today = LocalDate.now(clock);

        return repository.findAllNewestFirst().stream()
                .filter(entry -> from == null || !entry.getObservedOn().isBefore(from))
                .filter(entry -> to == null || !entry.getObservedOn().isAfter(to))
                .filter(entry -> crop.isBlank() || lower(entry.getCropName()).contains(crop)
                        || lower(entry.getVariety()).contains(crop))
                .filter(entry -> bed.isBlank() || lower(entry.getBedOrField()).contains(bed))
                .filter(entry -> tag.isBlank() || entry.getTags().contains(tag))
                .filter(entry -> !Boolean.TRUE.equals(safe.followUpDue())
                        || (entry.getFollowUpDate() != null
                        && !entry.getFollowUpDate().isAfter(today)))
                .filter(entry -> matchesPlainQuery(entry, plain.tokens()))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> distinctTags() {
        TreeSet<String> tags = new TreeSet<>();
        for (KnowledgeEntry entry : repository.findAllNewestFirst()) {
            tags.addAll(entry.getTags());
        }
        return List.copyOf(tags);
    }

    @Transactional
    public KnowledgeEntry create(KnowledgeEntry entry) {
        requireEntry(entry);
        entry.setId(null);
        validateEntry(entry);
        validateLinks(entry);
        entry.initializeTimestamps(Instant.now(clock));
        KnowledgeEntry saved = repository.save(entry);
        audit("KNOWLEDGE_LOG", saved, "Knowledge recorded");
        return saved;
    }

    @Transactional
    public KnowledgeEntry update(Long id, KnowledgeEntry incoming) {
        requireEntry(incoming);
        KnowledgeEntry existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No knowledge entry with id=" + id));
        validateEntry(incoming);
        validateLinks(incoming);
        existing.copyBusinessFieldsFrom(incoming);
        existing.touch(Instant.now(clock));
        KnowledgeEntry saved = repository.save(existing);
        audit("KNOWLEDGE_EDIT", saved, "Knowledge updated");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeEntry existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No knowledge entry with id=" + id));
        repository.deleteById(id);
        audit("KNOWLEDGE_DELETE", existing, "Knowledge removed");
    }

    private void validateEntry(KnowledgeEntry entry) {
        if (entry.getObservedOn().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Observation date cannot be in the future.");
        }
    }

    private void validateLinks(KnowledgeEntry entry) {
        requireExisting(entry.getHarvestId(), harvestRepository::findById, "harvest");
        requireExisting(entry.getInventoryItemId(), inventoryRepository::findById,
                "inventory item");
        requireExisting(entry.getOrderId(), orderRepository::findById, "order");
        requireExisting(entry.getCustomerId(), customerRepository::findById, "customer");
        Long exportId = entry.getFieldScienceExportId();
        if (exportId != null && (fieldScienceExportRepository == null
                || !fieldScienceExportRepository.existsById(exportId))) {
            throw new IllegalArgumentException(
                    "Linked field science export #" + exportId + " was not found.");
        }
    }

    private static <T> void requireExisting(Long id,
                                            java.util.function.Function<Long, Optional<T>> finder,
                                            String label) {
        if (id != null && finder.apply(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "Linked " + label + " #" + id + " was not found.");
        }
    }

    private void audit(String operation, KnowledgeEntry entry, String message) {
        syncHistoryService.record(
                "knowledge",
                operation,
                true,
                message + ": " + entry.getSummary(),
                "knowledgeId=" + entry.getId() + ", crop=" + entry.getCropName()
                        + ", tags=" + String.join("|", entry.getTags()),
                1);
    }

    private PlainQuery understand(String query) {
        String normalized = lower(query);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Month requestedMonth = null;
        Matcher words = WORD.matcher(normalized);
        while (words.find()) {
            String token = words.group();
            Month month = MONTHS.get(token);
            if (month != null) {
                requestedMonth = month;
            } else if (!STOP_WORDS.contains(token) && !YEAR.matcher(token).matches()) {
                tokens.add(singular(token));
            }
        }
        if (requestedMonth == null) {
            return new PlainQuery(List.copyOf(tokens), null, null);
        }

        Month queryMonth = requestedMonth;
        int year = requestedYear(normalized).orElseGet(() -> inferredYear(
                queryMonth, normalized.contains("last"), LocalDate.now(clock)));
        YearMonth month = YearMonth.of(year, queryMonth);
        return new PlainQuery(List.copyOf(tokens), month.atDay(1), month.atEndOfMonth());
    }

    private static Optional<Integer> requestedYear(String query) {
        Matcher matcher = YEAR.matcher(query);
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }

    private static int inferredYear(Month month, boolean explicitlyLast, LocalDate today) {
        YearMonth candidate = YearMonth.of(today.getYear(), month);
        if (explicitlyLast) {
            return candidate.atEndOfMonth().isBefore(today)
                    ? today.getYear() : today.getYear() - 1;
        }
        return candidate.atDay(1).isAfter(today) ? today.getYear() - 1 : today.getYear();
    }

    private static boolean matchesPlainQuery(KnowledgeEntry entry, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }
        String haystack = lower(String.join(" ",
                entry.getSummary(), entry.getKind().name(), entry.getKind().getLabel(),
                entry.getCropName(), entry.getVariety(), entry.getBedOrField(),
                entry.getProductionStage(), entry.getObservation(), entry.getActionTaken(),
                entry.getPerformedBy(), entry.getResult(), entry.getConfidence().name(),
                entry.getConfidence().getLabel(), String.join(" ", entry.getTags())));
        return tokens.stream().allMatch(haystack::contains);
    }

    private static String singular(String token) {
        return token.length() > 4 && token.endsWith("s") && !token.endsWith("ss")
                ? token.substring(0, token.length() - 1) : token;
    }

    private static String normalizeLookupTag(String value) {
        return KnowledgeEntry.normalizeTag(value);
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireEntry(KnowledgeEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Knowledge entry is required.");
        }
    }

    private static int requireLimit(int limit) {
        int normalized = limit <= 0 ? DEFAULT_LIMIT : limit;
        if (normalized > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "Knowledge search cannot return more than " + MAX_LIMIT + " entries.");
        }
        return normalized;
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("to date must be on or after from date.");
            }
            if (ChronoUnit.DAYS.between(from, to) > MAX_QUERY_DAYS) {
                throw new IllegalArgumentException("Date range cannot exceed 10 years.");
            }
        }
    }

    public record SearchRequest(String query, String crop, String bedOrField, String tag,
                                LocalDate from, LocalDate to, Boolean followUpDue, int limit) {
        public static SearchRequest all() {
            return new SearchRequest("", "", "", "", null, null, false, DEFAULT_LIMIT);
        }
    }

    private record PlainQuery(List<String> tokens, LocalDate from, LocalDate to) {
    }
}
