using { trekking as db } from '../db/schema';

@path: '/trekking'

service TrekkingService @(requires : 'authenticated-user'){
    
    @readonly
    entity Tours as projection on db.Tours;

    @(restrict : [
        {grant : 'CREATE'},
        {grant : 'READ', where : (createdBy = $user )}
    ])
    entity Bookings as projection on db.Bookings;

    @(requires : 'BookingAdmin')
    action cancelBooking(bookingId : UUID) returns String; 
}

@path : '/admin'

service AdminService @(requires : 'BookingAdmin'){

    entity Tours as projection on db.Tours; 
}