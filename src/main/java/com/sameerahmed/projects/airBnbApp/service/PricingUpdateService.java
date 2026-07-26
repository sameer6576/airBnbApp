package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import com.sameerahmed.projects.airBnbApp.repository.HotelMinPriceRepository;
import com.sameerahmed.projects.airBnbApp.repository.HotelRepository;
import com.sameerahmed.projects.airBnbApp.repository.InventoryRepository;
import com.sameerahmed.projects.airBnbApp.strategy.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PricingUpdateService {
    
    // Scheduler to update the inventory and hotelMinPrice tables every hour
    
    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final PricingService pricingService;
    private final HotelMinPriceService hotelMinPriceService;

    @Scheduled(cron = "0 0 */1 * * *")
//    @Scheduled(cron = "*/5 * * * * *")
    public void updatePrices(){
        int page = 0;
        int batchSize = 100;

        while(true){
            Page<Hotel> hotelPage = hotelRepository.findAll(PageRequest.of(page, batchSize));
            if(hotelPage.isEmpty()){
                break;
            }
            hotelPage.getContent().forEach(this::updateHotelPrices);

            page++;
        }
    }

    private void updateHotelPrices(Hotel hotel) {
        log.info("Updating hotel prices");

        List<Inventory> inventoryList =
                inventoryRepository.findByHotelAndDateBetween(
                        hotel,
                        LocalDate.now(),
                        LocalDate.now().plusYears(1));

        updateInventoryPrices(inventoryList);

        hotelMinPriceService.updateHotelMinPrice(hotel.getId());
    }

    private void updateInventoryPrices(List<Inventory> inventoryList){
        inventoryList.forEach(inventory -> {
            BigDecimal dynamicPrice = pricingService.calculateDynamicPricing(inventory);
            inventory.setPrice(dynamicPrice);
        });
        inventoryRepository.saveAll(inventoryList);
    }

}
