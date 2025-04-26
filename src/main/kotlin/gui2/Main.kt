package gui2

import com.formdev.flatlaf.FlatIntelliJLaf     // hell, neutral
import javax.swing.SwingUtilities
import javax.swing.UIManager

// import com.formdev.flatlaf.FlatDarkLaf      // dunkle Variante
// import com.formdev.flatlaf.intellijthemes.materialtheme.FlatGitHubDarkIJTheme // noch dunkler

fun main() = SwingUtilities.invokeLater {
    UIManager.setLookAndFeel(FlatIntelliJLaf())   // EINHEITLICHES Theme
    MainFrame().isVisible = true
}

