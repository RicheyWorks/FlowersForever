package com.flowerfarm.automation.farm;

import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.repository.CustomerJpaRepository;
import com.flowerfarm.repository.CustomerOrderJpaRepository;
import com.flowerfarm.repository.FloralRecipeJpaRepository;
import com.flowerfarm.repository.FieldAttachmentJpaRepository;
import com.flowerfarm.repository.GrowingTemplateJpaRepository;
import com.flowerfarm.repository.HarvestJpaRepository;
import com.flowerfarm.repository.ItemJpaRepository;
import com.flowerfarm.repository.KnowledgeJpaRepository;
import com.flowerfarm.repository.ProductionTaskJpaRepository;
import com.flowerfarm.repository.SeasonPlanJpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Performs bounded database reads for the AI data bridge. */
@Service
public class FarmDataPageService {

    public static final List<String> RECORD_TYPES = List.of(
            "inventory", "customers", "harvests", "orders", "knowledge",
            "production-tasks", "growing-templates", "planting-plans",
            "floral-recipes", "attachments");

    private static final long MAX_TASK_RANGE_DAYS = 1_830;
    private static final Sort INVENTORY_ORDER = Sort.by("id").ascending();
    private static final Sort CUSTOMER_ORDER = Sort.by("name").ascending()
            .and(Sort.by("id").ascending());
    private static final Sort HARVEST_ORDER = Sort.by("harvestDate").descending()
            .and(Sort.by("id").descending());
    private static final Sort SALES_ORDER = Sort.by("orderDate").descending()
            .and(Sort.by("id").descending());
    private static final Sort KNOWLEDGE_ORDER = Sort.by("observedOn").descending()
            .and(Sort.by("id").descending());
    private static final Sort TASK_ORDER = Sort.by("dueDate").ascending()
            .and(Sort.by("sequenceNumber").ascending())
            .and(Sort.by("id").ascending());
    private static final Sort TEMPLATE_ORDER = Sort.by("active").descending()
            .and(Sort.by("cropName").ascending())
            .and(Sort.by("name").ascending())
            .and(Sort.by("id").ascending());
    private static final Sort PLANTING_ORDER = Sort.by("seasonYear").descending()
            .and(Sort.by("sowDate").ascending())
            .and(Sort.by("bedOrField").ascending())
            .and(Sort.by("cropName").ascending())
            .and(Sort.by("id").ascending());
    private static final Sort RECIPE_ORDER = Sort.by("designType").ascending()
            .and(Sort.by("name").ascending())
            .and(Sort.by("id").ascending());
    private static final Sort ATTACHMENT_ORDER = Sort.by("capturedAt").descending()
            .and(Sort.by("id").descending());

    private final ItemJpaRepository items;
    private final CustomerJpaRepository customers;
    private final HarvestJpaRepository harvests;
    private final CustomerOrderJpaRepository orders;
    private final KnowledgeJpaRepository knowledge;
    private final ProductionTaskJpaRepository tasks;
    private final GrowingTemplateJpaRepository templates;
    private final SeasonPlanJpaRepository plantings;
    private final FloralRecipeJpaRepository recipes;
    private final FieldAttachmentJpaRepository attachments;

    public FarmDataPageService(
            ItemJpaRepository items,
            CustomerJpaRepository customers,
            HarvestJpaRepository harvests,
            CustomerOrderJpaRepository orders,
            KnowledgeJpaRepository knowledge,
            ProductionTaskJpaRepository tasks,
            GrowingTemplateJpaRepository templates,
            SeasonPlanJpaRepository plantings,
            FloralRecipeJpaRepository recipes,
            FieldAttachmentJpaRepository attachments) {
        this.items = items;
        this.customers = customers;
        this.harvests = harvests;
        this.orders = orders;
        this.knowledge = knowledge;
        this.tasks = tasks;
        this.templates = templates;
        this.plantings = plantings;
        this.recipes = recipes;
        this.attachments = attachments;
    }

    /** Reads one canonical record page without materializing the full table. */
    @Transactional(readOnly = true)
    public RecordPage<?> list(
            String type, int offset, int limit, LocalDate from, LocalDate to) {
        return switch (type) {
            case "inventory" -> page(items, offset, limit, INVENTORY_ORDER);
            case "customers" -> page(customers, offset, limit, CUSTOMER_ORDER);
            case "harvests" -> page(harvests, offset, limit, HARVEST_ORDER);
            case "orders" -> page(orders, offset, limit, SALES_ORDER);
            case "knowledge" -> page(knowledge, offset, limit, KNOWLEDGE_ORDER);
            case "production-tasks" -> taskPage(offset, limit, from, to);
            case "growing-templates" -> page(
                    templates, offset, limit, TEMPLATE_ORDER);
            case "planting-plans" -> page(
                    plantings, offset, limit, PLANTING_ORDER);
            case "floral-recipes" -> page(recipes, offset, limit, RECIPE_ORDER);
            case "attachments" -> page(
                    attachments, offset, limit, ATTACHMENT_ORDER);
            default -> throw new IllegalArgumentException(
                    "Unsupported record type '" + type + "'");
        };
    }

    private RecordPage<ProductionTask> taskPage(
            int offset, int limit, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return page(tasks, offset, limit, TASK_ORDER);
        }
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Task range must have a valid from and to date.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_TASK_RANGE_DAYS) {
            throw new IllegalArgumentException("Task range cannot exceed five years.");
        }
        Pageable pageable = new OffsetPageable(offset, limit, TASK_ORDER);
        List<ProductionTask> records = tasks.findByDueDateBetween(from, to, pageable);
        long total = tasks.countByDueDateBetween(from, to);
        return new RecordPage<>(records, offset, limit, total);
    }

    private static <T> RecordPage<T> page(
            JpaRepository<T, Long> repository, int offset, int limit, Sort sort) {
        Pageable pageable = new OffsetPageable(offset, limit, sort);
        List<T> records = repository.findAll(pageable).getContent();
        return new RecordPage<>(records, offset, limit, repository.count());
    }

    /** One stable, deterministically ordered slice of a canonical record type. */
    public record RecordPage<T>(List<T> records, int offset, int limit, long total) {

        public RecordPage {
            records = List.copyOf(records);
        }

        public boolean hasMore() {
            return (long) offset + records.size() < total;
        }
    }

    /** Pageable implementation that preserves the harness's arbitrary offset contract. */
    private record OffsetPageable(long offset, int pageSize, Sort sort) implements Pageable {

        private OffsetPageable {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must not be negative");
            }
            if (pageSize < 1) {
                throw new IllegalArgumentException("pageSize must be positive");
            }
            sort = sort == null ? Sort.unsorted() : sort;
        }

        @Override
        public int getPageNumber() {
            return Math.toIntExact(offset / pageSize);
        }

        @Override
        public int getPageSize() {
            return pageSize;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Sort getSort() {
            return sort;
        }

        @Override
        public Pageable next() {
            return new OffsetPageable(Math.addExact(offset, pageSize), pageSize, sort);
        }

        @Override
        public Pageable previousOrFirst() {
            return hasPrevious()
                    ? new OffsetPageable(Math.max(0, offset - pageSize), pageSize, sort)
                    : first();
        }

        @Override
        public Pageable first() {
            return new OffsetPageable(0, pageSize, sort);
        }

        @Override
        public Pageable withPage(int pageNumber) {
            if (pageNumber < 0) {
                throw new IllegalArgumentException("pageNumber must not be negative");
            }
            return new OffsetPageable(
                    Math.multiplyExact((long) pageNumber, pageSize), pageSize, sort);
        }

        @Override
        public boolean hasPrevious() {
            return offset > 0;
        }
    }
}
