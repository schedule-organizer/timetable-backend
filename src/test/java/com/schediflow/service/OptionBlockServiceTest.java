package com.schediflow.service;

import com.schediflow.domain.OptionBlock;
import com.schediflow.domain.OptionBlockGroup;
import com.schediflow.domain.TeachingGroup;
import com.schediflow.domain.TeachingGroupType;
import com.schediflow.dto.request.OptionBlockRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.OptionBlockGroupRepository;
import com.schediflow.repository.OptionBlockRepository;
import com.schediflow.repository.TeachingGroupRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionBlockServiceTest {

    @Mock OptionBlockRepository optionBlockRepository;
    @Mock OptionBlockGroupRepository optionBlockGroupRepository;
    @Mock TeachingGroupRepository teachingGroupRepository;

    OptionBlockService service;

    private static final Long TENANT_ID = 1L;
    private static final Long GROUP_A = 70L;
    private static final Long GROUP_B = 71L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new OptionBlockService(optionBlockRepository, optionBlockGroupRepository, teachingGroupRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_persistsBlockAndMembers() {
        stubGroups(TeachingGroupType.OPTION_BLOCK, GROUP_A, GROUP_B);
        when(optionBlockGroupRepository.findByTenantIdAndTeachingGroupIdIn(TENANT_ID, List.of(GROUP_A, GROUP_B)))
                .thenReturn(List.of());
        when(optionBlockRepository.save(any(OptionBlock.class))).thenAnswer(inv -> {
            OptionBlock b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 9L);
            return b;
        });

        var response = service.create(new OptionBlockRequest("Block A", " languages ", List.of(GROUP_A, GROUP_B)));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.description()).isEqualTo("languages");
        assertThat(response.memberGroupIds()).containsExactly(GROUP_A, GROUP_B);
        verify(optionBlockGroupRepository).deleteAllByOptionBlockId(9L);
        verify(optionBlockGroupRepository, times(2)).save(any(OptionBlockGroup.class));
    }

    @Test
    void create_blankDescription_isStoredAsNull() {
        stubGroups(TeachingGroupType.OPTION_BLOCK, GROUP_A, GROUP_B);
        when(optionBlockGroupRepository.findByTenantIdAndTeachingGroupIdIn(TENANT_ID, List.of(GROUP_A, GROUP_B)))
                .thenReturn(List.of());
        when(optionBlockRepository.save(any(OptionBlock.class))).thenAnswer(inv -> {
            OptionBlock b = inv.getArgument(0);
            ReflectionTestUtils.setField(b, "id", 9L);
            return b;
        });

        var response = service.create(new OptionBlockRequest("Block A", "   ", List.of(GROUP_A, GROUP_B)));

        assertThat(response.description()).isNull();
    }

    @Test
    void create_memberOfWrongType_throwsBadRequest() {
        stubGroups(TeachingGroupType.SET, GROUP_A, GROUP_B);

        assertThatThrownBy(() -> service.create(request(List.of(GROUP_A, GROUP_B))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("OPTION_BLOCK");
        verify(optionBlockRepository, never()).save(any());
    }

    @Test
    void create_unknownMember_throwsNotFound() {
        when(teachingGroupRepository.findByIdInAndTenantIdAndActive(List.of(GROUP_A, GROUP_B), TENANT_ID, true))
                .thenReturn(List.of(group(GROUP_A, TeachingGroupType.OPTION_BLOCK)));

        assertThatThrownBy(() -> service.create(request(List.of(GROUP_A, GROUP_B))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_duplicateMemberId_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(request(List.of(GROUP_A, GROUP_A))))
                .isInstanceOf(BadRequestException.class);
        verify(teachingGroupRepository, never()).findByIdInAndTenantIdAndActive(any(), any(), anyBoolean());
    }

    @Test
    void create_memberAlreadyInAnotherBlock_throwsConflict() {
        stubGroups(TeachingGroupType.OPTION_BLOCK, GROUP_A, GROUP_B);
        OptionBlockGroup taken = new OptionBlockGroup();
        taken.setOptionBlockId(4L);
        taken.setTeachingGroupId(GROUP_B);
        when(optionBlockGroupRepository.findByTenantIdAndTeachingGroupIdIn(TENANT_ID, List.of(GROUP_A, GROUP_B)))
                .thenReturn(List.of(taken));

        assertThatThrownBy(() -> service.create(request(List.of(GROUP_A, GROUP_B))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_keepsItsOwnMembers() {
        OptionBlock existing = new OptionBlock();
        ReflectionTestUtils.setField(existing, "id", 9L);
        when(optionBlockRepository.findByIdAndTenantIdAndActive(9L, TENANT_ID, true))
                .thenReturn(Optional.of(existing));
        stubGroups(TeachingGroupType.OPTION_BLOCK, GROUP_A, GROUP_B);
        OptionBlockGroup owned = new OptionBlockGroup();
        owned.setOptionBlockId(9L);
        owned.setTeachingGroupId(GROUP_A);
        when(optionBlockGroupRepository.findByTenantIdAndTeachingGroupIdIn(TENANT_ID, List.of(GROUP_A, GROUP_B)))
                .thenReturn(List.of(owned));
        when(optionBlockRepository.save(any(OptionBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(9L, new OptionBlockRequest("Renamed", null, List.of(GROUP_A, GROUP_B)));

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.memberGroupIds()).containsExactly(GROUP_A, GROUP_B);
    }

    @Test
    void delete_deactivatesAndReleasesMembers() {
        OptionBlock existing = new OptionBlock();
        ReflectionTestUtils.setField(existing, "id", 9L);
        existing.setActive(true);
        when(optionBlockRepository.findByIdAndTenantIdAndActive(9L, TENANT_ID, true))
                .thenReturn(Optional.of(existing));

        service.delete(9L);

        assertThat(existing.isActive()).isFalse();
        verify(optionBlockGroupRepository).deleteAllByOptionBlockId(9L);
    }

    @Test
    void delete_unknownBlock_throwsNotFound() {
        when(optionBlockRepository.findByIdAndTenantIdAndActive(9L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void membershipFacts_mapEveryJunctionRow() {
        OptionBlockGroup a = new OptionBlockGroup();
        a.setOptionBlockId(9L);
        a.setTeachingGroupId(GROUP_A);
        OptionBlockGroup b = new OptionBlockGroup();
        b.setOptionBlockId(9L);
        b.setTeachingGroupId(GROUP_B);
        when(optionBlockGroupRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(a, b));

        var facts = service.membershipFacts(TENANT_ID);

        assertThat(facts).hasSize(2);
        assertThat(facts.get(0).getOptionBlockId()).isEqualTo(9L);
        assertThat(facts).extracting(f -> f.getTeachingGroupId()).containsExactly(GROUP_A, GROUP_B);
    }

    private OptionBlockRequest request(List<Long> memberGroupIds) {
        return new OptionBlockRequest("Block", null, memberGroupIds);
    }

    private void stubGroups(TeachingGroupType type, Long... ids) {
        List<Long> idList = List.of(ids);
        when(teachingGroupRepository.findByIdInAndTenantIdAndActive(idList, TENANT_ID, true))
                .thenReturn(idList.stream().map(id -> group(id, type)).toList());
    }

    private TeachingGroup group(Long id, TeachingGroupType type) {
        TeachingGroup group = new TeachingGroup();
        ReflectionTestUtils.setField(group, "id", id);
        group.setType(type.name());
        return group;
    }
}
