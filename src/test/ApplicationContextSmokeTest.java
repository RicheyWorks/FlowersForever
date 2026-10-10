package com.flowerfarm;

import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.CannabisCultivationBatch;
import com.flowerfarm.model.CannabisCultivationStage;
import com.flowerfarm.model.CropAreaPlan;
import com.flowerfarm.model.CropAreaStage;
import com.flowerfarm.model.BerryBlockPlan;
import com.flowerfarm.model.BerryBlockStage;
import com.flowerfarm.model.BerrySalesChannel;
import com.flowerfarm.model.NurseryBatch;
import com.flowerfarm.model.NurseryBatchStage;
import com.flowerfarm.model.NurseryPlantForm;
import com.flowerfarm.model.NurserySalesChannel;
import com.flowerfarm.model.FarmEnterprise;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmState;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.FlowerOffering;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CannabisCultivationService;
import com.flowerfarm.service.CropAreaPlanService;
import com.flowerfarm.service.BerryBlockPlanService;
import com.flowerfarm.service.NurseryBatchService;
import com.flowerfarm.service.FarmOperationSettingsService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.headless=true",
                "flowerfarm.inventory.sample-enabled=true",
                "spring.datasource.url=jdbc:h2:mem:context-smoke;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "flowerfarm.inventory.seed-csv=target/no-context-smoke-seed.csv",
                "flowerfarm.irrigation.live-weather=false"
        })
@DisplayName("Application context smoke test")
class ApplicationContextSmokeTest {

    @Autowired
    ConnectorRegistry connectorRegistry;

    @Autowired HarnessRegistry harnessRegistry;
    @Autowired ApplicationContext applicationContext;

    @Autowired CustomerService customerService;
    @Autowired OrderService orderService;
    @Autowired InventoryService inventoryService;
    @Autowired FarmOperationSettingsService farmOperationSettingsService;
    @Autowired CannabisCultivationService cannabisCultivationService;
    @Autowired CropAreaPlanService cropAreaPlanService;
    @Autowired BerryBlockPlanService berryBlockPlanService;
    @Autowired NurseryBatchService nurseryBatchService;
    @Autowired UserDetailsService userDetailsService;

    @Test
    @DisplayName("starts the full REST application and registers every connector")
    void contextStartsWithAllConnectors() {
        assertThat(connectorRegistry.getConnectorNames()).containsExactlyInAnyOrder(
                "airtable",
                "csv",
                "excel",
                "farmbrite",
                "floranext",
                "google-sheets",
                "shopify",
                "square",
                "webhook");
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unused-default-user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("persists mixed farm setup and reloads it through the service boundary")
    void farmOperationSetupPersists() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setState(FarmState.OR);
        settings.setWorkspace(FarmWorkspace.CUSTOM_FARM);
        settings.setEnterprises(Set.of(FarmEnterprise.CUT_FLOWERS,
                FarmEnterprise.ORCHARDS, FarmEnterprise.ROW_CROPS));
        settings.setPrimaryEnterprise(FarmEnterprise.ORCHARDS);
        settings.setSpecialtyCrops(Set.of("Apples", "Roses", "Potatoes"));
        settings.setFlowerOfferings(Set.of(FlowerOffering.STEMS,
                FlowerOffering.WEDDINGS));

        FarmOperationSettings saved = farmOperationSettingsService.saveSettings(settings);
        FarmOperationSettings reloaded = farmOperationSettingsService.getSettings();

        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(reloaded).isNotSameAs(saved);
        assertThat(reloaded.getState()).isEqualTo(FarmState.OR);
        assertThat(reloaded.getWorkspace()).isEqualTo(FarmWorkspace.CUSTOM_FARM);
        assertThat(reloaded.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.ORCHARDS);
        assertThat(reloaded.getEnterprises()).containsExactly(
                FarmEnterprise.CUT_FLOWERS, FarmEnterprise.ROW_CROPS,
                FarmEnterprise.ORCHARDS);
        assertThat(reloaded.getSpecialtyCrops()).containsExactly(
                "Apples", "Potatoes", "Roses");
        assertThat(reloaded.getFlowerOfferings()).containsExactly(
                FlowerOffering.STEMS, FlowerOffering.WEDDINGS);
    }

    @Test
    @DisplayName("combined farm types retain their order and can be edited after reload")
    void combinedFarmTypesPersistAndCanBeEdited() {
        FarmOperationSettings settings = farmOperationSettingsService.getSettings();
        LinkedHashSet<FarmEnterprise> enterprises =
                new LinkedHashSet<>(settings.getEnterprises());
        enterprises.addAll(Set.of(FarmEnterprise.ROW_CROPS,
                FarmEnterprise.CANNABIS, FarmEnterprise.ORCHARDS));
        settings.setEnterprises(enterprises);
        settings.setWorkspace(FarmWorkspace.POTATO_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD)));

        farmOperationSettingsService.saveSettings(settings);
        FarmOperationSettings reloaded = farmOperationSettingsService.getSettings();
        assertThat(reloaded.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD);

        reloaded.setWorkspace(FarmWorkspace.APPLE_ORCHARD);
        reloaded.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.POTATO_FARM)));
        farmOperationSettingsService.saveSettings(reloaded);
        FarmOperationSettings edited = farmOperationSettingsService.getSettings();
        assertThat(edited.getWorkspace()).isEqualTo(FarmWorkspace.APPLE_ORCHARD);
        assertThat(edited.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.POTATO_FARM);
    }

    @Test
    @DisplayName("persists and updates a cultivation batch through the JPA service")
    void cultivationBatchPersists() {
        CannabisCultivationBatch request = new CannabisCultivationBatch(
                "Autumn greenhouse", "Cultivar A", "Greenhouse 2", 24,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1),
                CannabisCultivationStage.VEGETATIVE, "First local plan");

        CannabisCultivationBatch created = cannabisCultivationService.create(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(request.getId()).isNull();

        CannabisCultivationBatch edit = created.snapshot();
        edit.setPlantCount(28);
        edit.setStage(CannabisCultivationStage.FLOWERING);
        CannabisCultivationBatch updated = cannabisCultivationService.update(created.getId(), edit);
        CannabisCultivationBatch reloaded = cannabisCultivationService
                .findById(created.getId()).orElseThrow();

        assertThat(updated).isNotSameAs(edit);
        assertThat(reloaded.getPlantCount()).isEqualTo(28);
        assertThat(reloaded.getStage()).isEqualTo(CannabisCultivationStage.FLOWERING);
        assertThat(Duration.between(created.getCreatedAt(), reloaded.getCreatedAt()).abs())
                .isLessThanOrEqualTo(Duration.ofNanos(1_000));
        assertThat(cannabisCultivationService.listAll())
                .anySatisfy(batch -> assertThat(batch.getId()).isEqualTo(created.getId()));
    }

    @Test
    @DisplayName("persists potato and apple plans separately and prevents cross-type edits")
    void cropAreaPlansPersistByWorkspace() {
        CropAreaPlan potatoRequest = new CropAreaPlan(FarmWorkspace.POTATO_FARM,
                "North pivot", "Russet", new BigDecimal("18.25"),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 10, 1),
                CropAreaStage.GROWING, "First local plan");
        CropAreaPlan appleRequest = new CropAreaPlan(FarmWorkspace.APPLE_ORCHARD,
                "West block", "Honeycrisp", new BigDecimal("6.50"),
                null, LocalDate.of(2026, 9, 15), CropAreaStage.PLANNED, "");

        CropAreaPlan potato = cropAreaPlanService.create(potatoRequest);
        CropAreaPlan apple = cropAreaPlanService.create(appleRequest);
        assertThat(potato.getId()).isPositive();
        assertThat(apple.getId()).isPositive();
        assertThat(cropAreaPlanService.listByWorkspace(FarmWorkspace.POTATO_FARM))
                .extracting(CropAreaPlan::getId)
                .contains(potato.getId())
                .doesNotContain(apple.getId());
        assertThat(cropAreaPlanService.listByWorkspace(FarmWorkspace.APPLE_ORCHARD))
                .extracting(CropAreaPlan::getId)
                .contains(apple.getId())
                .doesNotContain(potato.getId());

        CropAreaPlan edit = potato.snapshot();
        edit.setStage(CropAreaStage.HARVEST_READY);
        CropAreaPlan updated = cropAreaPlanService.update(potato.getId(), edit);
        assertThat(updated.getStage()).isEqualTo(CropAreaStage.HARVEST_READY);
        assertThat(cropAreaPlanService.findById(potato.getId()).orElseThrow().getStage())
                .isEqualTo(CropAreaStage.HARVEST_READY);
        assertThatThrownBy(() -> cropAreaPlanService.update(potato.getId(), appleRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot move between farm workspaces");
    }

    @Test
    @DisplayName("persists a berry block plan and reloads its pick window")
    void berryBlockPlansPersist() {
        BerryBlockPlan request = new BerryBlockPlan("North rows", "Blueberries",
                "Duke", new BigDecimal("2.50"), LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 1),
                BerrySalesChannel.FRESH, BerryBlockStage.GROWING, "First local plan");

        BerryBlockPlan created = berryBlockPlanService.create(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(request.getId()).isNull();

        BerryBlockPlan edit = created.snapshot();
        edit.setStage(BerryBlockStage.PICKING);
        edit.setChannel(BerrySalesChannel.U_PICK);
        BerryBlockPlan updated = berryBlockPlanService.update(created.getId(), edit);
        BerryBlockPlan reloaded = berryBlockPlanService.findById(created.getId()).orElseThrow();

        assertThat(updated).isNotSameAs(edit);
        assertThat(reloaded.getStage()).isEqualTo(BerryBlockStage.PICKING);
        assertThat(reloaded.getChannel()).isEqualTo(BerrySalesChannel.U_PICK);
        assertThat(reloaded.getFirstPickDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(berryBlockPlanService.list())
                .anySatisfy(plan -> assertThat(plan.getId()).isEqualTo(created.getId()));
    }

    @Test
    @DisplayName("persists a nursery growing plan without changing stock or orders")
    void nurseryBatchPlansPersist() {
        NurseryBatch request = new NurseryBatch("Spring starts", "Dahlias",
                "Cafe au Lait", NurseryPlantForm.STARTS_OR_PLUGS, "Greenhouse 2",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 1), 120,
                NurserySalesChannel.RETAIL, NurseryBatchStage.GROWING, "First local plan");

        NurseryBatch created = nurseryBatchService.create(request);
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(request.getId()).isNull();

        NurseryBatch edit = created.snapshot();
        edit.setStage(NurseryBatchStage.READY_FOR_SALE);
        edit.setChannel(NurserySalesChannel.WHOLESALE);
        NurseryBatch updated = nurseryBatchService.update(created.getId(), edit);
        NurseryBatch reloaded = nurseryBatchService.find(created.getId()).orElseThrow();

        assertThat(updated).isNotSameAs(edit);
        assertThat(reloaded.getStage()).isEqualTo(NurseryBatchStage.READY_FOR_SALE);
        assertThat(reloaded.getChannel()).isEqualTo(NurserySalesChannel.WHOLESALE);
        assertThat(reloaded.getStartingCount()).isEqualTo(120);
        assertThat(nurseryBatchService.list())
                .anySatisfy(batch -> assertThat(batch.getId()).isEqualTo(created.getId()));
    }

    @Test
    @DisplayName("registers AI harness plugins but keeps their REST transport disabled")
    void harnessIsPluggableAndDisabledByDefault() {
        assertThat(harnessRegistry.descriptors())
                .extracting(descriptor -> descriptor.id())
                .contains("swing-desktop", "farm-data");
        assertThat(applicationContext.getBeansOfType(HarnessController.class)).isEmpty();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("serializes concurrent fulfillment so inventory is deducted once")
    void concurrentFulfillmentIsIdempotent() throws Exception {
        Customer customer = customerService.add(
                new Customer("Concurrency Test", "", "", "", "WHOLESALE", ""));
        CustomerOrder order = orderService.create(
                customer.getId(),
                LocalDate.now(),
                "CONFIRMED",
                "concurrency regression",
                "PICKUP",
                List.of(new OrderLine("Nootka Rose", 1, "stem", 2.5)));
        double quantityBefore = inventoryService.findByNameIgnoreCase("Nootka Rose")
                .map(Item::getQuantity)
                .orElseThrow();

        int requestCount = 12;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<CustomerOrder>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return orderService.fulfill(order.getId());
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<CustomerOrder> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo("FULFILLED");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(inventoryService.findByNameIgnoreCase("Nootka Rose"))
                .get()
                .extracting(Item::getQuantity)
                .isEqualTo(quantityBefore - 1);
    }
}
