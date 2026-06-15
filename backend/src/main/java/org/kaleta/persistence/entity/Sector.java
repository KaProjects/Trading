package org.kaleta.persistence.entity;

import lombok.Getter;

public enum Sector
{
    SEMICONDUCTORS("Semiconductors"),
    SOFTWARE("Technology - Software"),
    HARDWARE("Technology - Hardware"),
    CONSUMER_ELECTRONICS("Consumer Electronics"),
    COMMUNICATION_SERVICES("Communication Services"),
    ELECTRIC_VEHICLES("Electric Vehicles"),
    MOTOR_VEHICLES("Motor Vehicles"),
    INTERNET_RETAIL("Internet Retail "),
    ENERGY_MINERALS("Energy Minerals"),
    FINANCE("Finance"),
    MARINE_SHIPPING("Marine Shipping"),
    NON_DURABLES("Consumer Non-Durables"),
    AIRLINES("Airlines"),
    TRAVEL_SERVICES("Travel Services"),
    RESTAURANTS("Restaurants"),
    REAL_ESTATE("Real Estate"),
    AEROSPACE_DEFENSE("Aerospace & Defense"),
    HEALTH_TECH("Health Technology"),
    ELECTRICAL_PRODUCTS("Electrical Products"),
    ETF("ETF");

    @Getter
    private final String name;

    Sector(String name)
    {
        this.name = name;
    }
}
