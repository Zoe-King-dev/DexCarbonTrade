// Set up Ethers.js
let provider;
let signer;
let defaultAccount;

// Modern dapp browsers...
if (window.ethereum) {
    provider = new ethers.providers.Web3Provider(window.ethereum);
    // Request account access
    window.ethereum.request({ method: 'eth_requestAccounts' })
        .then(function (accounts) {
            defaultAccount = accounts[0];
            signer = provider.getSigner();
            console.log("Connected to MetaMask!");
            
            // Initialize the exchange
            init().then(() => {
                // fill in UI with current exchange rate:
                getPoolState().then((poolState) => {
                    $("#eth-token-rate-display").html("1 USDC = " + poolState['token_eth_rate'] + " CCT");
                    $("#token-eth-rate-display").html("1 CCT = " + poolState['eth_token_rate'] + " USDC");
                    $("#token-reserves").html(poolState['token_liquidity'] + " CCT");
                    $("#eth-reserves").html(poolState['eth_liquidity'] + " USDC");
                });
            });
            
            // Update account list
            var opts = accounts.map(function (a) { 
                return '<option value="'+a.toLowerCase()+'">'+a.toLowerCase()+'</option>' 
            });
            $(".account").html(opts);
        })
        .catch(function (error) {
            console.error(error);
        });
} else {
    console.error("Please install MetaMask!");
}

// Contract addresses and ABIs
const token_address = '0x5FbDB2315678afecb367f032d93F642f64180aa3';
const exchange_address = '0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512';

// Initialize contracts
const token_contract = new ethers.Contract(token_address, token_abi, signer);
const exchange_contract = new ethers.Contract(exchange_address, exchange_abi, signer);

// Pool state tracking
let poolState;

/*** INIT ***/
async function init() {
    poolState = await getPoolState();
    if (poolState['token_liquidity'] === 0 && poolState['eth_liquidity'] === 0) {
        const total_supply = 100000;
        await token_contract.connect(signer).mint(total_supply / 2);
        await token_contract.connect(signer).mint(total_supply / 2);
        await token_contract.connect(signer).disable_mint();
        await token_contract.connect(signer).approve(exchange_address, total_supply);
        await exchange_contract.connect(signer).createPool(5000, { value: ethers.utils.parseEther("5000")});
    }
}

async function getPoolState() {
    let poolReserves = await exchange_contract.connect(signer).getReserves();
    let liquidity_eth = Number(ethers.utils.formatEther(poolReserves[0]));
    let liquidity_tokens = Number(poolReserves[1])
    
    return {
        token_liquidity: liquidity_tokens,
        eth_liquidity: liquidity_eth,
        token_eth_rate: liquidity_tokens / liquidity_eth,
        eth_token_rate: liquidity_eth / liquidity_tokens,
    };
}

/*** TRADING FUNCTIONS ***/
async function addLiquidity(amountEth, maxSlippagePct) {
    await exchange_contract.connect(signer).addLiquidity({ 
        value: ethers.utils.parseEther(amountEth.toString()) 
    });
}

async function removeLiquidity(amountEth, maxSlippagePct) {
    await exchange_contract.connect(signer).removeLiquidity(
        ethers.utils.parseEther(amountEth.toString())
    );
}

async function removeAllLiquidity(maxSlippagePct) {
    await exchange_contract.connect(signer).removeAllLiquidity();
}

async function swapTokensForETH(amountToken, maxSlippagePct) {
    await token_contract.connect(signer).approve(exchange_address, amountToken);
    await exchange_contract.connect(signer).swapTokensForETH(amountToken);
}

async function swapETHForTokens(amountEth, maxSlippagePct) {
    await exchange_contract.connect(signer).swapETHForTokens({
        value: ethers.utils.parseEther(amountEth.toString())
    });
}

// Event Listeners
$("#swap-eth").click(async function() {
    try {
        await swapETHForTokens(
            $("#amt-to-swap").val(),
            $("#max-slippage-swap").val()
        );
        updateBalances();
    } catch (error) {
        console.error("Error swapping ETH for tokens:", error);
        alert("Error: " + error.message);
    }
});

$("#swap-token").click(async function() {
    try {
        await swapTokensForETH(
            $("#amt-to-swap").val(),
            $("#max-slippage-swap").val()
        );
        updateBalances();
    } catch (error) {
        console.error("Error swapping tokens for ETH:", error);
        alert("Error: " + error.message);
    }
});

$("#add-liquidity").click(async function() {
    try {
        await addLiquidity(
            $("#amt-eth").val(),
            $("#max-slippage-liquid").val()
        );
        updateBalances();
    } catch (error) {
        console.error("Error adding liquidity:", error);
        alert("Error: " + error.message);
    }
});

$("#remove-liquidity").click(async function() {
    try {
        await removeLiquidity(
            $("#amt-eth").val(),
            $("#max-slippage-liquid").val()
        );
        updateBalances();
    } catch (error) {
        console.error("Error removing liquidity:", error);
        alert("Error: " + error.message);
    }
});

$("#remove-all-liquidity").click(async function() {
    try {
        await removeAllLiquidity($("#max-slippage-liquid").val());
        updateBalances();
    } catch (error) {
        console.error("Error removing all liquidity:", error);
        alert("Error: " + error.message);
    }
}); 