package com.example.rnd_transit_mtl.View.together.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.group.GroupMember
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp sizes for one member row on the teal group panel.
private val MemberRowSpacing = 10.dp
private val MemberAvatarSize = 32.dp
private val MemberTagHorizontalPadding = 8.dp
private val MemberTagCornerRadius = 10.dp

/**
 * Lists a group's members on the teal panel, marking the host and the current user.
 *
 * @param members Members in join order.
 * @param hostId The group's host.
 * @param currentUserId The signed-in user, shown as "You".
 * @param modifier Configuration applied to the list column.
 */
@Composable
internal fun GroupMembersList(
    members: List<GroupMember>,
    hostId: UserId,
    currentUserId: UserId?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MemberRowSpacing)) {
        members.forEach { member ->
            val isHost = member.userId == hostId
            val isYou = member.userId == currentUserId
            val name = if (isYou) "${member.displayName} (you)" else member.displayName
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MemberRowSpacing),
                modifier = Modifier.semantics {
                    contentDescription = if (isHost) "$name, host" else name
                },
            ) {
                Box(
                    Modifier.size(MemberAvatarSize).background(TransitWhite, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        member.displayName.take(1).uppercase(),
                        color = TransitComplementary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    name,
                    color = TransitWhite,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isHost) {
                    Text(
                        "Host",
                        color = TransitComplementary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(TransitSelected, RoundedCornerShape(MemberTagCornerRadius))
                            .padding(horizontal = MemberTagHorizontalPadding, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
