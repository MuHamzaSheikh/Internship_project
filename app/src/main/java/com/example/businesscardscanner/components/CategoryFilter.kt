package com.example.businesscardscanner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryFilter(
    categories: List<String> = listOf("All", "Recents", "Colleague", "Vip", "Family"),
    defaultSelectedCategory: String = "All",
    onCategorySelected: (String) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(defaultSelectedCategory) }
    val scrollState = rememberScrollState()
    
    val selectedColor = Color(0xFF536DFE)
    val unselectedColor = Color.White
    val selectedTextColor = Color.White
    val unselectedTextColor = Color(0xFF333333)

    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory

            Surface(
                modifier = Modifier
                    .height(56.dp)
                    .then(
                        if (isSelected) {
                            Modifier.shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(28.dp),
                                spotColor = selectedColor.copy(alpha = 0.5f)
                            )
                        } else {
                            Modifier
                        }
                    ),
                shape = RoundedCornerShape(28.dp),
                color = if (isSelected) selectedColor else unselectedColor,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable {
                            selectedCategory = category
                            onCategorySelected(category)
                        }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) selectedTextColor else unselectedTextColor,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}
