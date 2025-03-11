package me.micartey.jation.network.packets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.micartey.jation.network.serializer.Serialize;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PacketAcknowledge {

    @Serialize("ackId")
    private int ackId;

}
