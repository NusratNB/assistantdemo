package com.example.buddycareassistant.gpt3documentation

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.example.buddycareassistant.R

class ParametersInfoActivity : AppCompatActivity() {

    private lateinit var tvModel: TextView
    private lateinit var tvMaxTokens: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvTopP: TextView
    private lateinit var tvN: TextView
    private lateinit var tvStream: TextView
    private lateinit var tvLogProbs: TextView
    private lateinit var tvStop: TextView
    private lateinit var tvPresencePenalty: TextView
    private lateinit var tvFrequencyPenalty: TextView
    private lateinit var tvBestOf: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parameters_info)

        tvModel = findViewById(R.id.tvModel)
        tvMaxTokens = findViewById(R.id.tvMaxTokens)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvTopP = findViewById(R.id.tvTopP)
        tvN = findViewById(R.id.tvN)
        tvStream = findViewById(R.id.tvStream)
        tvLogProbs = findViewById(R.id.tvLogProbs)
        tvStop = findViewById(R.id.tvStop)
        tvPresencePenalty = findViewById(R.id.tvPresencePenalty)
        tvFrequencyPenalty = findViewById(R.id.tvFrequencyPenalty)
        tvBestOf = findViewById(R.id.tvBestOf)

        tvModel.text =tvModel.text.toString() +  " ID of the model to use. (String. Required) "

        tvMaxTokens.text =tvMaxTokens.text.toString() + " The maximum number of tokens to generate in the completion." +
                    "The token count of your prompt plus max_tokens cannot exceed the model's" +
                    " context length. Most models have a context length of 2048 tokens (except for the " +
                    "newest models, which support 4096)(Integer. Optional. Default to 16)"

        tvTemperature.text = tvTemperature.text.toString() + " Higher values means the model will take more risks. Try 0.9 for more creative applications," +
                            " and 0 (argmax sampling) for ones with a well-defined answer." +
                            "Generally recommended altering this or top_p but not both. (Number. Optional. Default to 1)"

        tvTopP.text = tvTopP.text.toString() + " An alternative to sampling with temperature, called nucleus sampling, where " +
                "the model considers the results of the tokens with top_p probability mass. " +
                "So 0.1 means only the tokens comprising the top 10% probability mass are considered." +
                "Generally recommended altering this or temperature but not both. (Number. Optional. Defaults to 1)"

        tvN.text = tvN.text.toString() + " How many completions to generate for each prompt.\n"+
                   "Note: Because this parameter generates many completions, " +
                   "it can quickly consume your token quota. Use carefully and ensure that you have" +
                    " reasonable settings for max_tokens and stop. (Integer. Optional. Defaults to 1)"

        tvStream.text = tvStream.text.toString() + " Whether to stream back partial progress. If set, tokens will be sent as data-only" +
                        " server-sent events as they become available, with the stream terminated by a data: [DONE] message." +
                        "(Optional. Boolean. Default to false)"

        tvLogProbs.text = tvLogProbs.text.toString() + " Include the log probabilities on the logprobs most likely tokens, as well the chosen tokens. " +
                        "For example, if logprobs is 5, the API will return a list of the 5 most likely tokens. " +
                        "The API will always return the logprob of the sampled token, so there may be up to logprobs+1 elements in the response.\n" +
                "The maximum value for logprobs is 5. If you need more than this, please contact us through our Help center and describe your use case." +
                "(Integer. Optional. Defaults to null)"

        tvStop.text = tvStop.text.toString() + " Up to 4 sequences where the API will stop generating further tokens. The returned text will not contain the stop sequence." +
                "(String. Optional)"

        tvPresencePenalty.text = tvPresencePenalty.text.toString() + " Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they appear " +
                                "in the text so far, increasing the model's likelihood to talk about new topics. (Number. Optional. Defaults to 0)"

        tvFrequencyPenalty.text = tvFrequencyPenalty.text.toString() + " Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency " +
                        "in the text so far, decreasing the model's likelihood to repeat the same line verbatim. (Number. Optional. Defaults to 0)"

        tvBestOf.text = tvBestOf.text.toString() +" Generates best_of completions server-side and returns the \"best\" (the one with the highest log probability per token)." +
                " Results cannot be streamed.\n" +
                "\n" +
                "When used with n, best_of controls the number of candidate completions and n specifies how many to return – best_of must be greater than n.\n" +
                "\n" +
                "Note: Because this parameter generates many completions, it can quickly consume your token quota. Use carefully and ensure that you have reasonable " +
                "settings for max_tokens and stop (Integer. Optional. Defaults to 1)."












    }
}