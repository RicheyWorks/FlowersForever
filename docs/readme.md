Flower Farm Manager App Key Features<ul>

&nbsp; <li> <code>Inventory Management</code> – Add, edit, delete, search, and export items like plants, tools, and supplies.</li>

&nbsp; <li> <code>Rose Variety Suggestions</code> – Preloaded recommendations for Old Garden Roses and PNW-adapted varieties (Oregon/Western WA west of Cascades).</li>

&nbsp; <li> <code>Irrigation \& Pricing Tips</code> – Static guides tailored for Kitsap County and similar climates.</li>

&nbsp; <li> <code>Trend Analysis with AI/ML</code> – Use Weka for linear regression forecasting on sample inventory data (expandable to real historical logs).</li>

&nbsp; <li> <code>GUI \& CLI Interfaces</code> – User-friendly Swing GUI or text-based CLI for flexibility.</li>

&nbsp; <li> <code>Maven Build System</code> – Easy compilation and packaging into executable JAR.</li>

</ul>



<details>

<summary><strong> Item Class</strong></summary>



    The <code>Item</code> class represents an individual inventory item, such as a rose variety, tool, or supply. It includes validation for data integrity (e.g., non-negative prices) and methods for CSV export. Fields are immutable for safety.    Example Usage: Create a new rose item like <code>new Item("Bourbon Rose", "Flowers/Plants", 2.50, "Per Stem", 1.00, 100, "High fragrance")</code>. The <code>toString()</code> method provides a formatted display, and <code>toCsv()</code> handles escaping for export. Time complexity for operations is O(1).</details>

<details>

<summary><strong> InventoryManager Class</strong></summary>



    The <code>InventoryManager</code> class handles core operations like loading/saving from CSV, adding/editing/deleting items, and searching. It initializes with sample data including expanded rose varieties. Supports robust parsing for quoted CSV fields.    For example, call <code>addItem(new Item(...))</code> to add entries, or <code>searchItems("rose")</code> to find matches. Methods throw exceptions for invalid inputs, ensuring reliability. Time complexity: Add/Remove/Search O(1) average, O(N) worst; Load/Save O(N).</details>

<details>

<summary><strong> FlowerFarmGUI Class</strong></summary>



    The <code>FlowerFarmGUI</code> class provides a Swing-based interface with tabs for inventory, adding items (with rose type dropdown), tips, trends, and rose suggestions. Includes ML integration for analysis and sample rose addition button.    Launch via main app; interact with tables and buttons for CRUD operations. Error dialogs for invalid inputs. Rose tab lists varieties with details; add sample button populates inventory. Time complexity for UI updates O(N).</details>

<details>

<summary><strong> FlowerFarmCLI Class</strong></summary>



    The <code>FlowerFarmCLI</code> class offers a text-based interface for all features, including new "suggest-roses" command. Supports validation, confirmation prompts, and ML trends.    Run with "--cli"; use commands like "add" or "suggest-roses" for PNW rose ideas. Outputs formatted suggestions and handles errors gracefully. Time complexity similar to manager operations.</details>

<details>

<summary><strong> FlowerFarmApp Class</strong></summary>



    The <code>FlowerFarmApp</code> class is the entry point, launching GUI or CLI based on args. Includes "--help" for usage info.    Run <code>java -jar app.jar --cli</code> for CLI. Handles exceptions and provides clean startup. No complex computations; O(1) time.</details>

<details>

<summary><strong> Javadoc Documentation</strong></summary>

<details>

<summary><strong> View Image</strong></summary>

&nbsp;



!\[image](https://example.com/javadoc-screenshot.png)  <!-- Replace with actual if available -->



&nbsp;



</details>

</details>



<details>

<summary><strong> UML Class Diagram</strong></summary>

<details>

<summary><strong> View Image</strong></summary>

&nbsp;



!\[image](https://example.com/uml-diagram.png)  <!-- Replace with actual if available -->



&nbsp;



</details>

</details>



<details>

<summary><strong> JUnit Testing</strong></summary>

<details>

<summary><strong> View Image</strong></summary>

&nbsp;



!\[image](https://example.com/junit-tests.png)  <!-- Replace with actual if available -->



&nbsp;



</details>

</details>



<details>

<summary><strong> Works Cited: Dependencies \& Resources</strong></summary>



Core Java and DependenciesSwing (Java AWT)  Methods Used: JFrame, JTabbedPane, etc.  

Documentation: Java Swing API



Weka (ML Library)  Version: 3.8.6  

Used for: LinearRegression in trends.  

Documentation: Weka API



Maven (Build Tool)  Plugins: Assembly for JAR.  

Documentation: Maven Docs



Rose Varieties Info  Sources: Slides on Old Garden Roses; OSU/WSU extensions for PNW.  

Links: OSU Roses, WSU Gardening



</details>



<details>

<summary><strong> App Description</strong></summary>



The Flower Farm Manager is a robust tool for managing inventory in a PNW flower farm, with emphasis on rose varieties west of the Cascades. Supports adding custom items (plants, tools) via GUI/CLI, preloaded samples, ML trends, and regional tips. Built with Java, Maven, and Weka for portability.UsageBuild: mvn clean package

Run GUI: java -jar target/flowerfarm-manager-1.0-SNAPSHOT-jar-with-dependencies.jar

Run CLI: java -jar ... --cli

Help: --help



Expand tabs for rose suggestions; add via dropdown for auto-notes.</details>



<details>

<summary><strong> Adding More Items</strong></summary>



To add more plants/tools:GUI: Use "Add Item" tab; select category, fill fields. For roses, use dropdown to append type to notes.

CLI: "add" command; enter details prompted.

Code: Extend getSampleInventory() in InventoryManager for new defaults.

Varieties: Update roseTypes array in GUI for more options.



Supports unlimited additions; CSV persists changes.</details>



