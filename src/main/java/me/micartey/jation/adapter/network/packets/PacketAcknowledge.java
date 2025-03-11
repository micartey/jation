package me.micartey.jation.adapter.network.packets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.micartey.jation.adapter.network.serializer.Serialize;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PacketAcknowledge {

    @Serialize("ackId")
    private int ackId;

}
