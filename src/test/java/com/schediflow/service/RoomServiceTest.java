package com.schediflow.service;

import com.schediflow.domain.Room;
import com.schediflow.dto.request.RoomRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.RoomRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository roomRepository;

    RoomService service;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new RoomService(roomRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_returnsActiveRoomsForTenant() {
        Room r = buildRoom(1L, "Lab A");
        when(roomRepository.findByTenantIdAndActiveOrderByNameAsc(TENANT_ID, true))
                .thenReturn(List.of(r));

        assertThat(service.list()).hasSize(1);
        assertThat(service.list().get(0).name()).isEqualTo("Lab A");
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_whenNotFound_throwsResourceNotFound() {
        when(roomRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_returnsRoom() {
        Room r = buildRoom(1L, "Gym");
        when(roomRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(r));

        assertThat(service.getById(1L).name()).isEqualTo("Gym");
    }

    // D3: soft-deleted room is not returned by getById
    @Test
    void getById_softDeletedRoom_throwsResourceNotFound() {
        when(roomRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_withInvalidType_throwsBadRequest() {
        RoomRequest req = new RoomRequest("Lab A", "INVALID_TYPE", 30, List.of(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid room type");
    }

    // P3: lowercase type is normalized to uppercase
    @Test
    void create_withLowercaseType_normalizesAndSucceeds() {
        when(roomRepository.existsByNameAndTenantIdAndActive("Lab A", TENANT_ID, true)).thenReturn(false);
        Room saved = buildRoom(1L, "Lab A");
        saved.setType("LAB");
        when(roomRepository.save(any(Room.class))).thenReturn(saved);

        RoomRequest req = new RoomRequest("Lab A", "lab", 30, List.of(), null, null);
        var response = service.create(req);

        assertThat(response.type()).isEqualTo("LAB");
    }

    // P3: mixed case type is normalized
    @Test
    void create_withMixedCaseType_normalizesAndSucceeds() {
        when(roomRepository.existsByNameAndTenantIdAndActive("Gym A", TENANT_ID, true)).thenReturn(false);
        Room saved = buildRoom(2L, "Gym A");
        saved.setType("GYM");
        when(roomRepository.save(any(Room.class))).thenReturn(saved);

        RoomRequest req = new RoomRequest("Gym A", "Gym", 50, List.of(), null, null);
        var response = service.create(req);

        assertThat(response.type()).isEqualTo("GYM");
    }

    @Test
    void create_whenNameTaken_throwsConflict() {
        when(roomRepository.existsByNameAndTenantIdAndActive("Lab A", TENANT_ID, true)).thenReturn(true);

        RoomRequest req = new RoomRequest("Lab A", "LAB", 30, List.of(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_persists_andReturnsResponse() {
        when(roomRepository.existsByNameAndTenantIdAndActive("Lab A", TENANT_ID, true)).thenReturn(false);
        Room saved = buildRoom(42L, "Lab A");
        when(roomRepository.save(any(Room.class))).thenReturn(saved);

        RoomRequest req = new RoomRequest("Lab A", "LAB", 30, List.of("projector"), "Block A", "1F");
        var response = service.create(req);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("Lab A");
        assertThat(response.type()).isEqualTo("LAB");

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void create_withAllValidTypes_succeeds() {
        String[] types = {"CLASSROOM", "LAB", "GYM", "AUDITORIUM", "OTHER"};
        for (String type : types) {
            when(roomRepository.existsByNameAndTenantIdAndActive(type + " Room", TENANT_ID, true)).thenReturn(false);
            Room saved = buildRoom(1L, type + " Room");
            saved.setType(type);
            when(roomRepository.save(any(Room.class))).thenReturn(saved);

            var req = new RoomRequest(type + " Room", type, null, null, null, null);
            assertThat(service.create(req).type()).isEqualTo(type);
        }
    }

    // P5: error message lists valid types derived from enum, not hardcoded
    @Test
    void create_invalidType_errorMessageListsAllValidTypes() {
        RoomRequest req = new RoomRequest("Room X", "POOL", 10, List.of(), null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CLASSROOM")
                .hasMessageContaining("LAB")
                .hasMessageContaining("GYM")
                .hasMessageContaining("AUDITORIUM")
                .hasMessageContaining("OTHER");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_whenNotFound_throwsResourceNotFound() {
        when(roomRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        RoomRequest req = new RoomRequest("Lab B", "LAB", 20, List.of(), null, null);

        assertThatThrownBy(() -> service.update(99L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_whenNameTakenByOther_throwsConflict() {
        Room existing = buildRoom(1L, "Lab A");
        when(roomRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(roomRepository.existsByNameAndTenantIdAndActiveAndIdNot("Lab B", TENANT_ID, true, 1L))
                .thenReturn(true);

        RoomRequest req = new RoomRequest("Lab B", "LAB", 20, List.of(), null, null);

        assertThatThrownBy(() -> service.update(1L, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_persists() {
        Room existing = buildRoom(1L, "Lab A");
        when(roomRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(roomRepository.existsByNameAndTenantIdAndActiveAndIdNot("Lab B", TENANT_ID, true, 1L))
                .thenReturn(false);
        Room updated = buildRoom(1L, "Lab B");
        when(roomRepository.save(any(Room.class))).thenReturn(updated);

        RoomRequest req = new RoomRequest("Lab B", "LAB", 25, List.of("whiteboard"), null, null);
        var response = service.update(1L, req);

        assertThat(response.name()).isEqualTo("Lab B");
        verify(roomRepository).save(existing);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_whenNotFound_throwsResourceNotFound() {
        when(roomRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_setsActiveToFalse() {
        Room room = buildRoom(1L, "Lab A");
        when(roomRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);

        service.delete(1L);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    // D3: already-deleted room cannot be deleted again
    @Test
    void delete_alreadySoftDeleted_throwsResourceNotFound() {
        when(roomRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Room buildRoom(long id, String name) {
        Room r = new Room();
        try {
            var idField = Room.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(r, id);
            var ca = Room.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(r, OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setTenantId(TENANT_ID);
        r.setName(name);
        r.setType("LAB");
        r.setCapacity(30);
        r.setEquipmentTags(List.of());
        r.setActive(true);
        return r;
    }
}
