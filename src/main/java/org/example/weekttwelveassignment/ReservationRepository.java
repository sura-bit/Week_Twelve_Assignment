package org.example.weekttwelveassignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByRoomIdOrderByStartTime(Long roomId);

    boolean existsByRoomId(Long roomId);

    @Query("""
        select r from Reservation r
        join fetch r.room
        join fetch r.owner o
        where o.username = :username
        order by r.startTime
    """)
    List<Reservation> findMine(@Param("username") String username);

    @Query("""
        select count(r) from Reservation r
        where r.room.id = :roomId
          and r.startTime < :endTime
          and r.endTime > :startTime
        """)
    long countOverlapping(@Param("roomId") Long roomId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    @Query("""
    select count(r) from Reservation r
    where r.room.id = :roomId
      and r.id <> :id
      and r.startTime < :endTime
      and r.endTime > :startTime
    """)
    long countOverlappingExcept(@Param("roomId") Long roomId,
                                @Param("id") Long id,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

}
