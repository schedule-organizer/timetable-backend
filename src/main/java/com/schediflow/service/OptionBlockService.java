package com.schediflow.service;

import com.schediflow.domain.OptionBlock;
import com.schediflow.domain.OptionBlockGroup;
import com.schediflow.domain.TeachingGroup;
import com.schediflow.domain.TeachingGroupType;
import com.schediflow.dto.request.OptionBlockRequest;
import com.schediflow.dto.response.OptionBlockResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.OptionBlockGroupRepository;
import com.schediflow.repository.OptionBlockRepository;
import com.schediflow.repository.TeachingGroupRepository;
import com.schediflow.security.TenantContext;
import com.schediflow.solver.model.OptionBlockMembership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OptionBlockService {

    private final OptionBlockRepository optionBlockRepository;
    private final OptionBlockGroupRepository optionBlockGroupRepository;
    private final TeachingGroupRepository teachingGroupRepository;

    public OptionBlockService(
            OptionBlockRepository optionBlockRepository,
            OptionBlockGroupRepository optionBlockGroupRepository,
            TeachingGroupRepository teachingGroupRepository) {
        this.optionBlockRepository = optionBlockRepository;
        this.optionBlockGroupRepository = optionBlockGroupRepository;
        this.teachingGroupRepository = teachingGroupRepository;
    }

    public List<OptionBlockResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        List<OptionBlock> blocks = optionBlockRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true);
        if (blocks.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> membersByBlock = loadMemberIds(blocks.stream().map(OptionBlock::getId).toList());
        return blocks.stream()
                .map(b -> toResponse(b, membersByBlock.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    public OptionBlockResponse getById(Long id) {
        OptionBlock block = findOrThrow(id);
        return toResponse(block, memberGroupIdsOf(block.getId()));
    }

    @Transactional
    public OptionBlockResponse create(OptionBlockRequest req) {
        Long tenantId = TenantContext.getTenantId();
        List<Long> memberGroupIds = validateMembers(tenantId, req.memberGroupIds(), null);

        OptionBlock block = new OptionBlock();
        block.setTenantId(tenantId);
        block.setName(req.name().trim());
        block.setDescription(trimToNull(req.description()));
        OptionBlock saved = optionBlockRepository.save(block);

        replaceMembers(tenantId, saved.getId(), memberGroupIds);
        return toResponse(saved, memberGroupIds);
    }

    @Transactional
    public OptionBlockResponse update(Long id, OptionBlockRequest req) {
        Long tenantId = TenantContext.getTenantId();
        OptionBlock block = findOrThrow(id);
        List<Long> memberGroupIds = validateMembers(tenantId, req.memberGroupIds(), id);

        block.setName(req.name().trim());
        block.setDescription(trimToNull(req.description()));
        OptionBlock saved = optionBlockRepository.save(block);

        replaceMembers(tenantId, id, memberGroupIds);
        return toResponse(saved, memberGroupIds);
    }

    /** Deactivates the block and releases its member groups so they can join another block. */
    @Transactional
    public void delete(Long id) {
        OptionBlock block = findOrThrow(id);
        block.setActive(false);
        optionBlockRepository.save(block);
        optionBlockGroupRepository.deleteAllByOptionBlockId(id);
    }

    /**
     * Solver problem facts: every (option block, teaching group) pair in the tenant. Lessons whose
     * teaching groups share a block must be scheduled in the same period slot.
     */
    public List<OptionBlockMembership> membershipFacts(Long tenantId) {
        return optionBlockGroupRepository.findByTenantId(tenantId).stream()
                .map(m -> new OptionBlockMembership(m.getOptionBlockId(), m.getTeachingGroupId()))
                .toList();
    }

    public List<Long> memberGroupIdsOf(Long optionBlockId) {
        return optionBlockGroupRepository.findByOptionBlockIdOrderByTeachingGroupIdAsc(optionBlockId).stream()
                .map(OptionBlockGroup::getTeachingGroupId)
                .toList();
    }

    private OptionBlock findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return optionBlockRepository
                .findByIdAndTenantIdAndActive(id, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Option block not found: " + id));
    }

    /**
     * Verifies every member group exists and is active in the tenant, is of type {@code OPTION_BLOCK},
     * and is not already claimed by a different block. Returns the ids in request order.
     */
    private List<Long> validateMembers(Long tenantId, List<Long> requested, Long excludeBlockId) {
        List<Long> memberGroupIds = new ArrayList<>(new LinkedHashSet<>(requested));
        if (memberGroupIds.size() != requested.size()) {
            throw new BadRequestException("Duplicate teaching group id in memberGroupIds");
        }
        if (memberGroupIds.contains(null)) {
            throw new BadRequestException("memberGroupIds must not contain null");
        }

        List<TeachingGroup> groups =
                teachingGroupRepository.findByIdInAndTenantIdAndActive(memberGroupIds, tenantId, true);
        Map<Long, TeachingGroup> byId =
                groups.stream().collect(Collectors.toMap(TeachingGroup::getId, g -> g));
        for (Long groupId : memberGroupIds) {
            TeachingGroup group = byId.get(groupId);
            if (group == null) {
                throw new ResourceNotFoundException("Teaching group not found: " + groupId);
            }
            if (!TeachingGroupType.OPTION_BLOCK.name().equals(group.getType())) {
                throw new BadRequestException(
                        "Teaching group " + groupId + " is of type " + group.getType()
                                + "; option block members must be of type OPTION_BLOCK");
            }
        }

        optionBlockGroupRepository.findByTenantIdAndTeachingGroupIdIn(tenantId, memberGroupIds).stream()
                .filter(m -> !m.getOptionBlockId().equals(excludeBlockId))
                .findFirst()
                .ifPresent(m -> {
                    throw new ConflictException(
                            "Teaching group " + m.getTeachingGroupId()
                                    + " already belongs to option block " + m.getOptionBlockId());
                });

        return memberGroupIds;
    }

    private void replaceMembers(Long tenantId, Long blockId, List<Long> memberGroupIds) {
        optionBlockGroupRepository.deleteAllByOptionBlockId(blockId);
        for (Long groupId : memberGroupIds) {
            OptionBlockGroup member = new OptionBlockGroup();
            member.setTenantId(tenantId);
            member.setOptionBlockId(blockId);
            member.setTeachingGroupId(groupId);
            optionBlockGroupRepository.save(member);
        }
    }

    private Map<Long, List<Long>> loadMemberIds(List<Long> blockIds) {
        if (blockIds.isEmpty()) {
            return Map.of();
        }
        return optionBlockGroupRepository.findByOptionBlockIdInOrderByTeachingGroupIdAsc(blockIds).stream()
                .collect(Collectors.groupingBy(
                        OptionBlockGroup::getOptionBlockId,
                        Collectors.mapping(OptionBlockGroup::getTeachingGroupId, Collectors.toList())));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OptionBlockResponse toResponse(OptionBlock block, List<Long> memberGroupIds) {
        return new OptionBlockResponse(
                block.getId(),
                block.getName(),
                block.getDescription(),
                memberGroupIds,
                block.isActive(),
                block.getCreatedAt());
    }
}
