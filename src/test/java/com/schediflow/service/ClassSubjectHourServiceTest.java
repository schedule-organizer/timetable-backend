package com.schediflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.ClassSubjectHour;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Subject;
import com.schediflow.domain.Tenant;
import com.schediflow.dto.SpreadPattern;
import com.schediflow.dto.request.ClassSubjectHourItemRequest;
import com.schediflow.dto.request.ClassSubjectHoursReplaceRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassSubjectHourServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long CLASS_ID = 50L;
    private static final Long SUBJECT_A = 100L;
    private static final Long SUBJECT_B = 101L;

    @Mock ClassSubjectHourRepository classSubjectHourRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock BellScheduleRepository bellScheduleRepository;
    @Mock SchedulePeriodRepository schedulePeriodRepository;
    @Mock TenantRepository tenantRepository;

    ObjectMapper objectMapper = new ObjectMapper();
    ClassSubjectHourService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service =
                new ClassSubjectHourService(
                        classSubjectHourRepository,
                        schoolClassRepository,
                        subjectRepository,
                        bellScheduleRepository,
                        schedulePeriodRepository,
                        tenantRepository,
                        objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_whenClassMissing_throws() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_ID, TENANT_ID, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(CLASS_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(classSubjectHourRepository, never()).findByTenantIdAndClassIdOrderBySubjectIdAsc(any(), any());
    }

    @Test
    void list_returnsRows() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_ID, TENANT_ID, true))
                .thenReturn(Optional.of(new SchoolClass()));
        ClassSubjectHour row = new ClassSubjectHour();
        row.setSubjectId(SUBJECT_A);
        row.setPeriodsPerCycle(3);
        row.setSpreadPattern("SPREAD");
        when(classSubjectHourRepository.findByTenantIdAndClassIdOrderBySubjectIdAsc(TENANT_ID, CLASS_ID))
                .thenReturn(List.of(row));

        var out = service.list(CLASS_ID);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).subjectId()).isEqualTo(SUBJECT_A);
        assertThat(out.get(0).periodsPerCycle()).isEqualTo(3);
        assertThat(out.get(0).spreadPattern()).isEqualTo(SpreadPattern.SPREAD);
    }

    @Test
    void replace_duplicateSubject_throws() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_ID, TENANT_ID, true))
                .thenReturn(Optional.of(new SchoolClass()));
        var req =
                new ClassSubjectHoursReplaceRequest(
                        List.of(
                                new ClassSubjectHourItemRequest(SUBJECT_A, 1, SpreadPattern.ANY),
                                new ClassSubjectHourItemRequest(SUBJECT_A, 2, SpreadPattern.ANY)));

        assertThatThrownBy(() -> service.replace(CLASS_ID, req)).isInstanceOf(BadRequestException.class);
        verify(classSubjectHourRepository, never()).deleteByTenantIdAndClassId(any(), any());
    }

    @Test
    void replace_subjectMissing_throws() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_ID, TENANT_ID, true))
                .thenReturn(Optional.of(new SchoolClass()));
        when(subjectRepository.findByIdAndTenantIdAndActive(SUBJECT_A, TENANT_ID, true))
                .thenReturn(Optional.empty());
        var req =
                new ClassSubjectHoursReplaceRequest(
                        List.of(new ClassSubjectHourItemRequest(SUBJECT_A, 1, SpreadPattern.CLUSTER)));

        assertThatThrownBy(() -> service.replace(CLASS_ID, req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void replace_exceedsCapacity_throws() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_ID, TENANT_ID, true))
                .thenReturn(Optional.of(new SchoolClass()));
        stubSubject(SUBJECT_A);
        stubDefaultBellOneSlotPerDay();
        var req =
                new ClassSubjectHoursReplaceRequest(
                        List.of(new ClassSubjectHourItemRequest(SUBJECT_A, 6, SpreadPattern.ANY)));

        assertThatThrownBy(() -> service.replace(CLASS_ID, req)).isInstanceOf(BadRequestException.class);
        verify(classSubjectHourRepository, never()).deleteByTenantIdAndClassId(any(), any());
    }

    @Test
    void replace_deletesThenSaves() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_ID, TENANT_ID, true))
                .thenReturn(Optional.of(new SchoolClass()));
        stubSubject(SUBJECT_A);
        stubSubject(SUBJECT_B);
        stubDefaultBellOneSlotPerDay();

        ClassSubjectHour savedA = new ClassSubjectHour();
        savedA.setSubjectId(SUBJECT_A);
        savedA.setPeriodsPerCycle(2);
        savedA.setSpreadPattern("SPREAD");
        ClassSubjectHour savedB = new ClassSubjectHour();
        savedB.setSubjectId(SUBJECT_B);
        savedB.setPeriodsPerCycle(3);
        savedB.setSpreadPattern("CLUSTER");

        when(classSubjectHourRepository.save(any(ClassSubjectHour.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(classSubjectHourRepository.findByTenantIdAndClassIdOrderBySubjectIdAsc(TENANT_ID, CLASS_ID))
                .thenReturn(List.of(savedA, savedB));

        var req =
                new ClassSubjectHoursReplaceRequest(
                        List.of(
                                new ClassSubjectHourItemRequest(SUBJECT_A, 2, SpreadPattern.SPREAD),
                                new ClassSubjectHourItemRequest(SUBJECT_B, 3, SpreadPattern.CLUSTER)));

        service.replace(CLASS_ID, req);

        var inOrder = inOrder(classSubjectHourRepository);
        inOrder.verify(classSubjectHourRepository).deleteByTenantIdAndClassId(TENANT_ID, CLASS_ID);
        ArgumentCaptor<ClassSubjectHour> captor = ArgumentCaptor.forClass(ClassSubjectHour.class);
        inOrder.verify(classSubjectHourRepository, times(2)).save(captor.capture());
        inOrder.verify(classSubjectHourRepository).findByTenantIdAndClassIdOrderBySubjectIdAsc(TENANT_ID, CLASS_ID);
        assertThat(captor.getAllValues())
                .extracting(ClassSubjectHour::getSubjectId, ClassSubjectHour::getPeriodsPerCycle)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(SUBJECT_A, 2),
                        org.assertj.core.groups.Tuple.tuple(SUBJECT_B, 3));
    }

    private void stubSubject(Long id) {
        Subject s = new Subject();
        ReflectionTestUtils.setField(s, "id", id);
        when(subjectRepository.findByIdAndTenantIdAndActive(id, TENANT_ID, true)).thenReturn(Optional.of(s));
    }

    /** One teaching slot per day × default 5-day cycle = 5 periods capacity */
    private void stubDefaultBellOneSlotPerDay() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(new Tenant()));
        BellSchedule bell = new BellSchedule();
        ReflectionTestUtils.setField(bell, "id", 9L);
        when(bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(TENANT_ID)).thenReturn(List.of(bell));
        SchedulePeriod p = new SchedulePeriod();
        p.setBreak(false);
        p.setLunch(false);
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(9L)).thenReturn(List.of(p));
    }
}
