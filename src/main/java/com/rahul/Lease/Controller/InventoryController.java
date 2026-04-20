package com.rahul.Lease.Controller;

import com.rahul.Lease.Dto.InventoryDTO;
import com.rahul.Lease.Dto.UpdateInventoryRequestDTO;
import com.rahul.Lease.Services.Interfaces.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/inventory")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin Inventory", description = "Manage hotel room inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Fetch inventory of Room",
            description = "fetch all the inventory items for given room id", tags = {"Admin Inventory"})
    public ResponseEntity<List<InventoryDTO>> getAllInventoryByRoom(@PathVariable long roomId){
        return ResponseEntity.ok(inventoryService.getAllInventoryByRoom(roomId));
    }
    @GetMapping("rooms/{roomId}")
    @Operation(summary = "Update inventory for a room", description = "Modify inventory details for specific room")
    public ResponseEntity<Void> updateInventory(@PathVariable long roomId,
                                                @RequestBody UpdateInventoryRequestDTO
                                                    updateInventoryRequestDTO){
        inventoryService.updateInventory(roomId, updateInventoryRequestDTO);
        return ResponseEntity.noContent().build();
    }
}
