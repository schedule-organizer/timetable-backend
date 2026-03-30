package com.schediflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.domain.Tenant;
import com.schediflow.dto.request.AcademicYearRequest;
import com.schediflow.dto.request.BellScheduleRequest;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionSeedServiceTest {

    @Mock
    AcademicYearRepository academicYearRepository;

    @Mock
    AcademicYearService academicYearService;

    @Mock
    BellScheduleService bellScheduleService;

    @Mock
    TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InstitutionSeedService service;

    @BeforeEach
    void setUp() {
        service = new InstitutionSeedService(
                academicYearRepository,
                academicYearService,
                bellScheduleService,
                tenantRepository,
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void seedDefaults_whenNoAcademicYears_createsYearBellScheduleAndSettings() {
        when(academicYearRepository.count()).thenReturn(0L);
        Tenant tenant = new Tenant();
        tenant.setSettings("{}");
        when(tenantRepository.findById(5L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaults(5L);

        assertThat(TenantContext.getTenantId()).isNull();
        verify(academicYearService).create(eq(5L), any(AcademicYearRequest.class));
        verify(bellScheduleService).create(eq(5L), any(BellScheduleRequest.class));
        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void seedDefaults_whenAcademicYearsExist_isNoOp() {
        when(academicYearRepository.count()).thenReturn(1L);

        service.seedDefaults(5L);

        verify(academicYearService, never()).create(any(), any());
        verify(bellScheduleService, never()).create(any(), any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void seedDefaults_mergesSchedulingCycleAndTerminologyInSettings() throws Exception {
        when(academicYearRepository.count()).thenReturn(0L);
        Tenant tenant = new Tenant();
        tenant.setSettings("{}");
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        when(tenantRepository.save(tenantCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaults(7L);

        JsonNode settings = objectMapper.readTree(tenantCaptor.getValue().getSettings());
        assertThat(settings.get("locale").asText()).isEqualTo("en_GB");
        assertThat(settings.get("timezone").asText()).isEqualTo("UTC");
        assertThat(settings.get("terminology").get("class").asText()).isEqualTo("Class");
        assertThat(settings.get("schedulingCycle").get("daysInCycle").asInt()).isEqualTo(5);
        assertThat(settings.get("schedulingCycle").get("labels")).hasSize(5);
    }

    @Test
    void seedDefaults_whenSettingsJsonIsMalformed_mergesDefaults() throws Exception {
        when(academicYearRepository.count()).thenReturn(0L);
        Tenant tenant = new Tenant();
        tenant.setSettings("{not-json");
        when(tenantRepository.findById(8L)).thenReturn(Optional.of(tenant));
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        when(tenantRepository.save(tenantCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaults(8L);

        JsonNode settings = objectMapper.readTree(tenantCaptor.getValue().getSettings());
        assertThat(settings.get("locale").asText()).isEqualTo("en_GB");
        assertThat(settings.get("schedulingCycle").get("daysInCycle").asInt()).isEqualTo(5);
    }

    @Test
    void seedDefaults_whenSettingsJsonIsNonObject_mergesDefaults() throws Exception {
        when(academicYearRepository.count()).thenReturn(0L);
        Tenant tenant = new Tenant();
        tenant.setSettings("[]");
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant));
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        when(tenantRepository.save(tenantCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.seedDefaults(9L);

        JsonNode settings = objectMapper.readTree(tenantCaptor.getValue().getSettings());
        assertThat(settings.get("locale").asText()).isEqualTo("en_GB");
    }
}
