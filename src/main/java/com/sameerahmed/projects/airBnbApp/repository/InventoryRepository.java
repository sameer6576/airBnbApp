package com.sameerahmed.projects.airBnbApp.repository;

import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.Inventory;
import com.sameerahmed.projects.airBnbApp.entity.Room;
import com.sameerahmed.projects.airBnbApp.repository.projection.DailyMinPrice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Two different date conventions live in here, and mixing them up is the easiest
 * way to break this application. Read this before touching a predicate.
 *
 * <p><b>Stay ranges</b> use {@code date >= :checkInDate AND date < :checkOutDate}.
 * The check-out day is not a night: a guest arriving on the 1st and leaving on
 * the 2nd occupies one night and is charged for one, and the 2nd stays on sale
 * for the next guest. Parameters are named checkInDate / checkOutDate.
 *
 * <p><b>Administrative ranges</b> use {@code BETWEEN :startDate AND :endDate} and
 * are inclusive on both ends, because a manager closing "the 1st to the 5th"
 * means five days, and a pricing sweep over a window means the whole window.
 * Parameters are named startDate / endDate.
 *
 * <p>Converting an administrative range to the exclusive form would silently
 * shorten it by a day, with nothing throwing. Keep the naming honest.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    void deleteByRoom(Room room);

    List<Inventory> findByRoomOrderByDate(Room room);

    // ----- stay ranges: check-out exclusive -----------------------------------

    @Query("""
                SELECT i
                FROM Inventory i
                WHERE i.room.id = :roomId
                    AND i.date >= :checkInDate
                    AND i.date < :checkOutDate
                    AND i.closed = false
                    AND (i.totalCount - i.bookedCount - i.reservedCount) >= :roomsCount
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findAndLockAvailableInventory(
            @Param("roomId") Long roomId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate,
            @Param("roomsCount") Integer roomsCount
    );

    /**
     * Locks every night of a stay, unfiltered. Locking is not the place to
     * enforce availability — a predicate here would exclude the very rows the
     * following update needs to hold, leaving that update to run unlocked.
     */
    @Query("""
                SELECT i
                FROM Inventory i
                WHERE i.room.id = :roomId
                    AND i.date >= :checkInDate
                    AND i.date < :checkOutDate
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> lockStayRange(@Param("roomId") Long roomId,
                                  @Param("checkInDate") LocalDate checkInDate,
                                  @Param("checkOutDate") LocalDate checkOutDate
    );

    /**
     * Every count-changing statement below returns the number of rows it touched.
     * Each carries its own WHERE guard, so a caller that ignores the count cannot
     * tell "updated every night" from "updated none of them" — which is how a
     * guest ends up charged for nights that were never actually held.
     *
     * `flushAutomatically` matters because these run after the owning Booking has
     * been written in the same transaction. `clearAutomatically` is deliberately
     * not set: it would detach the caller's Booking, and the notification path
     * then walks booking.getUser() lazily.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.reservedCount = i.reservedCount + :numberOfRooms
            WHERE i.room.id = :roomId
                AND i.date >= :checkInDate
                AND i.date < :checkOutDate
                AND (i.totalCount - i.bookedCount - i.reservedCount) >= :numberOfRooms
                AND i.closed = false
            """)
    int initBooking(@Param("roomId") Long roomId,
                     @Param("checkInDate") LocalDate checkInDate,
                     @Param("checkOutDate") LocalDate checkOutDate,
                     @Param("numberOfRooms") int numberOfRooms
    );

    // No `closed` check: the guest has already paid, so a date the manager closed
    // after the hold was taken must still be confirmable.
    @Modifying(flushAutomatically = true)
    @Query("""
                    UPDATE Inventory i
                    SET i.reservedCount = i.reservedCount - :numberOfRooms,
                        i.bookedCount = i.bookedCount + :numberOfRooms
                    WHERE i.room.id = :roomId
                        AND i.date >= :checkInDate
                        AND i.date < :checkOutDate
                        AND (i.totalCount - i.bookedCount) >= :numberOfRooms
                        AND i.reservedCount >= :numberOfRooms
            """)
    int confirmBooking(@Param("roomId") Long roomId,
                        @Param("checkInDate") LocalDate checkInDate,
                        @Param("checkOutDate") LocalDate checkOutDate,
                        @Param("numberOfRooms") int numberOfRooms
    );

    // Guarding a decrement on remaining capacity instead of on bookedCount let
    // bookedCount go negative, which then inflated availability everywhere,
    // because every check computes totalCount - bookedCount - reservedCount.
    // A release must also never be gated on `closed`, or closing a date would
    // strand the rooms permanently.
    @Modifying(flushAutomatically = true)
    @Query("""
                    UPDATE Inventory i
                    SET i.bookedCount = i.bookedCount - :numberOfRooms
                    WHERE i.room.id = :roomId
                        AND i.date >= :checkInDate
                        AND i.date < :checkOutDate
                        AND i.bookedCount >= :numberOfRooms
            """)
    int cancelBooking(@Param("roomId") Long roomId,
                       @Param("checkInDate") LocalDate checkInDate,
                       @Param("checkOutDate") LocalDate checkOutDate,
                       @Param("numberOfRooms") int numberOfRooms
    );

    @Modifying(flushAutomatically = true)
    @Query("""
                    UPDATE Inventory i
                    SET i.reservedCount = i.reservedCount - :numberOfRooms
                    WHERE i.room.id = :roomId
                        AND i.date >= :checkInDate
                        AND i.date < :checkOutDate
                        AND i.reservedCount >= :numberOfRooms
            """)
    int cancelReservation(@Param("roomId") Long roomId,
                           @Param("checkInDate") LocalDate checkInDate,
                           @Param("checkOutDate") LocalDate checkOutDate,
                           @Param("numberOfRooms") int numberOfRooms
    );

    // ----- administrative ranges: both ends inclusive -------------------------

    List<Inventory> findByHotelAndDateBetween(Hotel hotel, LocalDate startDate, LocalDate endDate);

    List<Inventory> findByRoomAndDateGreaterThanEqual(Room room, LocalDate fromDate);

    /**
     * Inventory carries a denormalised copy of the hotel's city because search
     * filters on it. Renaming a hotel's city without this leaves every inventory
     * row pointing at the old one, so the hotel vanishes from search for its new
     * city and keeps matching the old.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Inventory i SET i.city = :city WHERE i.hotel.id = :hotelId")
    int updateCityForHotel(@Param("hotelId") Long hotelId, @Param("city") String city);

    /**
     * The dates a room already has inventory for. Used to generate only the
     * missing ones, which makes horizon top-up idempotent and gap-filling rather
     * than assuming the existing rows are contiguous.
     */
    @Query("""
                    SELECT i.date FROM Inventory i
                    WHERE i.room.id = :roomId
                        AND i.date BETWEEN :startDate AND :endDate
            """)
    List<LocalDate> findExistingDates(@Param("roomId") Long roomId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate
    );

    /** Locks an inclusive administrative window before a manager-driven update. */
    @Query("""
                    SELECT i from Inventory i
                    WHERE i.room.id = :roomId
                        AND i.date BETWEEN :startDate AND :endDate
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> getInventoryAndLockBeforeUpdate(@Param("roomId") Long roomId,
                         @Param("startDate") LocalDate startDate,
                         @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query("""
                    UPDATE Inventory i
                    SET i.surgeFactor = :surgeFactor,
                        i.closed = :closed
                    WHERE i.room.id = :roomId
                        AND i.date BETWEEN :startDate AND :endDate
            """)
    void updateInventory(@Param("roomId") Long roomId,
                         @Param("startDate") LocalDate startDate,
                         @Param("endDate") LocalDate endDate,
                         @Param("closed") boolean closed,
                         @Param("surgeFactor") BigDecimal surgeFactor
    );

    @Query("""
            SELECT
                i.date as date,
                MIN(i.price) as price
            FROM Inventory i
            WHERE i.hotel = :hotel
                AND i.date BETWEEN :startDate AND :endDate
            GROUP BY i.date
            ORDER BY i.date
            """)
    List<DailyMinPrice> findDailyMinimumPrices(
            @Param("hotel") Hotel hotel,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
