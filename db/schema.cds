namespace trekking; 

using { cuid, managed } from '@sap/cds/common';

entity Tours : cuid, managed {
  key ID : UUID;
  name : String(111);
  description : String(1000);
  price : Decimal(9,2);
  currency : String(3);
  difficulty : Integer;
  duration : Integer;
  image : String(255);
  availableSeats : Integer;
  startDate : Date;
  endDate : Date;
}

entity Bookings : cuid, managed {
    tour           : Association to Tours;
    customerName   : String(200);
    participants   : Integer;
    totalPrice     : Decimal(12,2);
    status         : String(20);
    phone          : String(30); 
}