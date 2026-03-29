package com.schediflow.service;

import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.dto.request.BellScheduleRequest;
import com.schediflow.dto.request.PeriodRequest;
import com.schediflow.dto.response.BellScheduleResponse;
import com.schediflow.dto.response.PeriodResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
public class BellScheduleService {

    private final BellScheduleRepository bellScheduleRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;

    public BellScheduleService(BellScheduleRepository bellScheduleRepository,
                                SchedulePeriodRepository schedulePeriodRepository) {
        this.bellScheduleRepository = bellScheduleRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
    }

    public List<BellScheduleResponse> list() {
        // TenantFilterAspect activates tenantFilter — no explicit tenantId needed in query
        return bellScheduleRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public BellScheduleResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public BellScheduleResponse create(Long tenantId, BellScheduleRequest req) {
        validatePeriods(req.periods());
        if (req.isDefault()) {
            deactivateCurrentDefault(tenantId);
        }
        BellSchedule entity = new BellSchedule();
        entity.setTenantId(tenantId);
        entity.setName(req.name());
        entity.setDefaultSchedule(req.isDefault());
        BellSchedule saved = bellScheduleRepository.save(entity);
        savePeriodsForSchedule(saved.getId(), tenantId, req.periods());
        return toResponseWithPeriods(saved, req.periods().size());
    }

    @Transactional
    public BellScheduleResponse update(Long tenantId, Long id, BellScheduleRequest req) {
        BellSchedule entity = findOrThrow(id);
        validatePeriods(req.periods());
        if (req.isDefault() && !entity.isDefaultSchedule()) {
            deactivateCurrentDefault(tenantId);
        }
        entity.setName(req.name());
        entity.setDefaultSchedule(req.isDefault());
        BellSchedule saved = bellScheduleRepository.save(entity);
        schedulePeriodRepository.deleteAllByBellScheduleId(id);
        savePeriodsForSchedule(saved.getId(), tenantId, req.periods());
        return toResponseWithPeriods(saved, req.periods().size());
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        BellSchedule entity = findOrThrow(id);
        if (entity.isDefaultSchedule()) {
            long defaultCount = bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(tenantId).size();
            if (defaultCount <= 1) {
                throw new BadRequestException("Cannot delete the only default bell schedule");
            }
        }
        schedulePeriodRepository.deleteAllByBellScheduleId(id);
        bellScheduleRepository.delete(entity);
    }

    private BellSchedule findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return bellScheduleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bell schedule not found: " + id));
    }

    private void deactivateCurrentDefault(Long tenantId) {
        bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(tenantId)
                .forEach(s -> {
                    s.setDefaultSchedule(false);
                    bellScheduleRepository.save(s);
                });
    }

    private void validatePeriods(List<PeriodRequest> periods) {
        for (int i = 0; i < periods.size(); i++) {
            PeriodRequest a = periods.get(i);
            if (!a.startTime().isBefore(a.endTime())) {
                throw new BadRequestException(
                        "Period '" + a.name() + "': startTime must be before endTime");
            }
            for (int j = i + 1; j < periods.size(); j++) {
                PeriodRequest b = periods.get(j);
                if (periodsOverlap(a.startTime(), a.endTime(), b.startTime(), b.endTime())) {
                    throw new BadRequestException(
                            "Periods overlap: '" + a.name() + "' and '" + b.name() + "'");
                }
            }
        }
    }

    private boolean periodsOverlap(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private void savePeriodsForSchedule(Long bellScheduleId, Long tenantId, List<PeriodRequest> periods) {
        for (PeriodRequest req : periods) {
            SchedulePeriod period = new SchedulePeriod();
            period.setBellScheduleId(bellScheduleId);
            period.setTenantId(tenantId);
            period.setName(req.name());
            period.setStartTime(req.startTime());
            period.setEndTime(req.endTime());
            period.setBreak(req.isBreak());
            period.setLunch(req.isLunch());
            period.setOrdinal(req.ordinal());
            schedulePeriodRepository.save(period);
        }
    }

    private BellScheduleResponse toResponse(BellSchedule entity) {
        List<SchedulePeriod> periods =
                schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(entity.getId());
        List<PeriodResponse> periodResponses = periods.stream()
                .map(p -> new PeriodResponse(
                        p.getId(),
                        p.getName(),
                        p.getStartTime(),
                        p.getEndTime(),
                        p.isBreak(),
                        p.isLunch(),
                        p.getOrdinal()))
                .toList();
        return new BellScheduleResponse(
                entity.getId(),
                entity.getName(),
                entity.isDefaultSchedule(),
                periodResponses,
                entity.getCreatedAt());
    }

    private BellScheduleResponse toResponseWithPeriods(BellSchedule entity, int expectedPeriodCount) {
        // After a write operation, fetch the persisted periods
        return toResponse(entity);
    }
}
